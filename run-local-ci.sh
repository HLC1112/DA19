
# --- 准备工作 ---
# 请确保您已经通过 'export GEMINI_API_KEY=您的密钥' 设置了环境变量
if [ -z "$GEMINI_API_KEY" ]; then
    echo "错误：请先设置 GEMINI_API_KEY 环境变量！"
    exit 1
fi

echo "--- 开始本地CI构建 ---"

# --- 1. 运行构建，并将日志同时输出到控制台和文件 ---
# 如果构建成功，脚本将在这里正常退出
./gradlew build | tee build.log && echo "--- 构建成功！---" && exit 0

# --- 如果上面的命令失败，代码将继续执行到这里 ---
echo "--- 构建失败，正在启动AI分析... ---"

# --- 2. 调用脚本收集上下文信息 ---
CONTEXT=$(bash scripts/collect-context.sh)

# --- 3. 调用Gemini API进行分析 ---
echo "--- 正在请求AI分析，请稍候... ---"

# 准备发送给Gemini的JSON数据
JSON_PAYLOAD=$(jq -n --arg prompt "请根据以下CI构建失败的上下文信息，分析可能的原因并给出修复建议。上下文信息如下：\n\n$CONTEXT" \
  '{contents: [{parts: [{text: $prompt}]}]}')

# 调用Gemini API
AI_RESPONSE=$(curl -s -X POST "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY" \
  -H "Content-Type: application/json" \
  -d "$JSON_PAYLOAD")

# --- 4. 提取并打印AI的分析结果 ---
AI_COMMENT=$(echo "$AI_RESPONSE" | jq -r '.candidates[0].content.parts[0].text')

echo ""
echo "========================================"
echo "🤖 AI自动分析报告 🤖"
echo "========================================"
echo "你好！我检测到本次构建失败了，以下是基于日志和代码变更的初步分析："
echo ""
echo -e "$AI_COMMENT" # 使用-e来正确处理换行符
echo "========================================"