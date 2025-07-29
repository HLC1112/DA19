#!/bin/bash

# 文件路径: scripts/analyze_failure.sh
# 描述: 当CI测试失败时，此脚本负责收集上下文信息，并调用Gemini AI进行分析。

# --- Helper Functions ---
print_header() {
    echo "========================================================================"
    echo "  $1"
    echo "========================================================================"
}

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

TEST_REPORTS_PATH="build/test-results/test"
FAILED_REPORTS=$(grep -l -r "failure" "$TEST_REPORTS_PATH" | tr '\n' ' ')

if [ -z "$FAILED_REPORTS" ]; then
    FAILURE_CONTEXT="CI job failed, but no specific test failure reports were found. It might be a compilation or configuration error."
    # Fallback to git show if git diff fails (e.g., on first commit)
    CODE_DIFF=$(git diff HEAD~1 HEAD 2>/dev/null || git show HEAD)
    PROMPT_CONTEXT="Git Diff:\n\`\`\`diff\n$CODE_DIFF\n\`\`\`\n\nFailure Context:\n$FAILURE_CONTEXT"
else
    echo "📄 找到了失败的测试报告: $FAILED_REPORTS"

    CODE_DIFF=$(git diff HEAD~1 HEAD 2>/dev/null || git show HEAD)
    FAILURE_LOGS=$(cat $FAILED_REPORTS)
    FAILED_CLASS_NAME=$(echo "$FAILURE_LOGS" | grep -o 'classname="[^"]*"' | head -n 1 | cut -d'"' -f2)

    SOURCE_CODE_CONTENT="无法定位源文件。"
    if [ ! -z "$FAILED_CLASS_NAME" ]; then
        FILE_PATH="src/test/kotlin/$(echo $FAILED_CLASS_NAME | tr '.' '/').kt"
        echo "   - 关联的测试文件: $FILE_PATH"
        if [ -f "$FILE_PATH" ]; then
            SOURCE_CODE_CONTENT=$(cat "$FILE_PATH")
        else
             # Fallback for other test files that might exist
            FILE_PATH_ALT_1="src/test/kotlin/com/aifactory/chat/ExampleFailureTest.kt"
            FILE_PATH_ALT_2="src/test/kotlin/com/aifactory/chat/app/AppL1FsmTest.kt"
             if [ -f "$FILE_PATH_ALT_1" ]; then
                SOURCE_CODE_CONTENT=$(cat "$FILE_PATH_ALT_1")
             elif [ -f "$FILE_PATH_ALT_2" ]; then
                SOURCE_CODE_CONTENT=$(cat "$FILE_PATH_ALT_2")
             else
                echo "   - 警告: 未能在任何预期路径找到源文件。"
             fi
        fi
    fi

    PROMPT_CONTEXT="Git Diff:\n\`\`\`diff\n$CODE_DIFF\n\`\`\`\n\nFailed Test Source Code:\n\`\`\`kotlin\n$SOURCE_CODE_CONTENT\n\`\`\`\n\nJUnit Failure Log:\n\`\`\`xml\n$FAILURE_LOGS\n\`\`\`"
fi

# --- 2. 构建发送给AI的Prompt ---
# 关键修正：先在shell中构建完整的Prompt文本，然后安全地传递给jq。
FULL_PROMPT_TEXT=$(cat <<EOF
You are an expert Senior Kotlin/Java Software Engineer specializing in CI/CD diagnostics. A CI build has failed. Your task is to analyze the provided context (git diff, source code, and failure logs) to determine the root cause and provide a concise, actionable solution.

Here is the context:

$PROMPT_CONTEXT

Based on this information, please provide:
1.  **Root Cause Analysis**: A brief, clear explanation of why the test failed.
2.  **Suggested Fix**: A concrete code snippet or step-by-step instructions to fix the issue.
EOF
)

PROMPT_JSON=$(jq -n \
  --arg prompt_text "$FULL_PROMPT_TEXT" \
  '{
    "contents": [
      {
        "parts": [
          {
            "text": $prompt_text
          }
        ]
      }
    ]
  }')

# --- 3. 调用Gemini API ---
print_header "🧠 正在将上下文发送给Gemini进行分析..."

API_URL="https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${GEMINI_API_KEY}"

AI_RESPONSE=$(curl -s -f -X POST -H "Content-Type: application/json" -d "$PROMPT_JSON" "$API_URL")

if [ $? -ne 0 ]; then
    echo "❌ 调用Gemini API失败。请检查您的API密钥和网络连接。"
    # 打印jq生成的JSON以供调试
    echo "--- Generated JSON Payload ---"
    echo "$PROMPT_JSON"
    echo "----------------------------"
    exit 1
fi

# --- 4. 解析并显示AI的反馈 ---
print_header "✅ AI分析报告"

AI_ANALYSIS=$(echo "$AI_RESPONSE" | jq -r '.candidates[0].content.parts[0].text')

if [ -z "$AI_ANALYSIS" ] || [ "$AI_ANALYSIS" == "null" ]; then
    echo "😕 AI未能返回有效的分析结果。原始响应如下:"
    echo "$AI_RESPONSE"
else
    echo "$AI_ANALYSIS"
fi

print_header "AIOps分析流程结束"
