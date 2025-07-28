#!/bin/bash

# 文件路径: scripts/analyze_failure.sh
# 描述: 当CI测试失败时，此脚本负责收集上下文信息，并调用Gemini AI进行分析。

# --- Helper Functions ---
# 打印美化的标题
print_header() {
    echo "========================================================================"
    echo "  $1"
    echo "========================================================================"
}

# 检查必要的工具是否存在
check_tools() {
    for tool in git find grep cat curl jq; do
        if ! command -v $tool &> /dev/null; then
            echo "错误: 必需的命令 '$tool' 未找到。请确保它已安装并位于您的PATH中。"
            exit 1
        fi
    done
    if [ -z "$GEMINI_API_KEY" ]; then
        echo "错误: 环境变量 'GEMINI_API_KEY' 未设置。请通过act的--secret参数传入。"
        exit 1
    fi
}

# --- Main Logic ---
check_tools

print_header "🔬 CI测试失败 - 启动AIOps根本原因分析"

# --- 1. 收集“案情材料” ---
echo "🔍 正在收集上下文信息..."

# 查找所有JUnit XML测试报告
TEST_REPORTS_PATH="build/test-results/test"
FAILED_REPORTS=$(grep -l -r "failure" "$TEST_REPORTS_PATH" | tr '\n' ' ')

if [ -z "$FAILED_REPORTS" ]; then
    echo "🤔 未找到明确的失败报告，但任务状态为失败。可能是编译或配置错误。"
    # 在这种情况下，我们可以发送更通用的日志
    FAILURE_CONTEXT="CI job failed, but no specific test failure reports were found. It might be a compilation or configuration error."
    CODE_DIFF=$(git diff HEAD~1 HEAD)
    PROMPT_CONTEXT="Git Diff:\n\`\`\`diff\n$CODE_DIFF\n\`\`\`\n\nFailure Context:\n$FAILURE_CONTEXT"
else
    echo "📄 找到了失败的测试报告: $FAILED_REPORTS"

    # 获取最近一次提交的代码变更
    CODE_DIFF=$(git diff HEAD~1 HEAD)

    # 读取失败报告的内容
    FAILURE_LOGS=$(cat $FAILED_REPORTS)

    # 从报告中提取失败的测试类名
    # 这是一个简化的提取方法，适用于标准JUnit XML
    FAILED_CLASS_NAME=$(echo "$FAILURE_LOGS" | grep -o 'classname="[^"]*"' | head -n 1 | cut -d'"' -f2)

    SOURCE_CODE_CONTENT="无法定位源文件。"
    if [ ! -z "$FAILED_CLASS_NAME" ]; then
        # 将类名转换为文件路径
        FILE_PATH="src/test/kotlin/$(echo $FAILED_CLASS_NAME | tr '.' '/').kt"
        echo "   - 关联的测试文件: $FILE_PATH"
        if [ -f "$FILE_PATH" ]; then
            SOURCE_CODE_CONTENT=$(cat "$FILE_PATH")
        else
            echo "   - 警告: 未能在路径 '$FILE_PATH' 找到源文件。"
        fi
    fi

    PROMPT_CONTEXT="Git Diff:\n\`\`\`diff\n$CODE_DIFF\n\`\`\`\n\nFailed Test Source Code ($FILE_PATH):\n\`\`\`kotlin\n$SOURCE_CODE_CONTENT\n\`\`\`\n\nJUnit Failure Log:\n\`\`\`xml\n$FAILURE_LOGS\n\`\`\`"
fi

# --- 2. 构建发送给AI的Prompt ---
PROMPT_JSON=$(jq -n \
  --arg context "$PROMPT_CONTEXT" \
  '{
    "contents": [
      {
        "parts": [
          {
            "text": "You are an expert Senior Kotlin/Java Software Engineer specializing in CI/CD diagnostics. A CI build has failed. Your task is to analyze the provided context (git diff, source code, and failure logs) to determine the root cause and provide a concise, actionable solution.\n\nHere is the context:\n\n" + $context + "\n\nBased on this information, please provide:\n1.  **Root Cause Analysis**: A brief, clear explanation of why the test failed.\n2.  **Suggested Fix**: A concrete code snippet or step-by-step instructions to fix the issue."
          }
        ]
      }
    ]
  }')

# --- 3. 调用Gemini API ---
print_header "🧠 正在将上下文发送给Gemini进行分析..."

API_URL="https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${GEMINI_API_KEY}"

# 使用curl发送请求，-s表示静默模式，-S显示错误，-f在HTTP错误时失败
AI_RESPONSE=$(curl -s -f -X POST -H "Content-Type: application/json" -d "$PROMPT_JSON" "$API_URL")

if [ $? -ne 0 ]; then
    echo "❌ 调用Gemini API失败。请检查您的API密钥和网络连接。"
    exit 1
fi

# --- 4. 解析并显示AI的反馈 ---
print_header "✅ AI分析报告"

# 使用jq解析JSON响应，提取模型生成的内容
AI_ANALYSIS=$(echo "$AI_RESPONSE" | jq -r '.candidates[0].content.parts[0].text')

if [ -z "$AI_ANALYSIS" ] || [ "$AI_ANALYSIS" == "null" ]; then
    echo "😕 AI未能返回有效的分析结果。原始响应如下:"
    echo "$AI_RESPONSE"
else
    # 格式化输出
    echo "$AI_ANALYSIS"
fi

print_header "AIOps分析流程结束"
