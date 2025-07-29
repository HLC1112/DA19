#!/bin/bash

# 文件路径: scripts/upload_telemetry.sh
# 描述: 此脚本负责解析JUnit XML测试报告，并将其中的数据转换为JSON格式，
#       然后上报到本地运行的OpenSearch实例中。

# --- Configuration ---
OPENSEARCH_URL="http://host.docker.internal:9200"
INDEX_NAME="test-results"
TEST_REPORTS_PATH="build/test-results/test"

# --- Helper Functions ---
print_header() {
    echo "========================================================================"
    echo "  $1"
    echo "========================================================================"
}

check_opensearch() {
    if ! curl -s --head --connect-timeout 5 "$OPENSEARCH_URL" | head -n 1 | grep "200 OK" > /dev/null; then
        echo "❌ 无法连接到OpenSearch实例于 $OPENSEARCH_URL"
        echo "   请确保您的docker-compose环境正在运行，并且Docker内存充足。"
        exit 1
    fi
    echo "✅ 成功连接到本地OpenSearch实例。"
}

# --- Main Logic ---
print_header "📊 上报遥测数据到本地DEP (OpenSearch)"
check_opensearch

# 查找所有XML报告
XML_FILES=$(find "$TEST_REPORTS_PATH" -name "*.xml")

if [ -z "$XML_FILES" ]; then
    echo "❌ 错误: 在预设路径 '$TEST_REPORTS_PATH' 下未找到任何 JUnit XML 测试报告。"
    echo "   数据上报中止。"
    exit 1
fi

echo "✅ 成功找到了以下测试报告，准备上传:"
echo "$XML_FILES"
echo ""

# 确保索引存在于OpenSearch中
# 注意：即使索引已存在，这个命令也只是返回一个错误，不会中断流程，是安全的。
curl -s -X PUT "$OPENSEARCH_URL/$INDEX_NAME" -H 'Content-Type: application/json' -d'
{
  "mappings": {
    "properties": {
      "timestamp": { "type": "date" },
      "job_id": { "type": "keyword" },
      "test_suite": { "type": "keyword" },
      "test_case": { "type": "keyword" },
      "duration_sec": { "type": "float" },
      "status": { "type": "keyword" },
      "error_message": { "type": "text" },
      "system_out": { "type": "text" }
    }
  }
}
'
echo "" # for newline

# 逐个处理XML文件
for file in $XML_FILES; do
    # 使用awk解析XML并生成OpenSearch的Bulk API格式
    JSON_PAYLOAD=$(awk '
    BEGIN { RS="<testcase"; FS="\"" }
    /name=/ && /classname=/ {
        test_case_name = $2;
        class_name = $4;

        # 使用 "+ 0" 技巧强制将时间字符串转换为纯数字
        time_as_number = $6 + 0;

        status = "PASSED";
        error_message = "";
        system_out = "";

        if ($0 ~ /<failure/) {
            status = "FAILED";
            match($0, /message="([^"]*)"/, arr);
            error_message = arr[1];
            # 健壮的JSON转义
            gsub(/\\/, "\\\\", error_message); gsub(/"/, "\\\"", error_message); gsub(/\n/, "\\n", error_message); gsub(/\r/, "\\r", error_message); gsub(/\t/, "\\t", error_message);
        }
        if ($0 ~ /<system-out>/) {
            match($0, /<system-out><!\[CDATA\[(.*?)\]\]><\/system-out>/, arr);
            system_out = arr[1];
            # 健壮的JSON转义
            gsub(/\\/, "\\\\", system_out); gsub(/"/, "\\\"", system_out); gsub(/\n/, "\\n", system_out); gsub(/\r/, "\\r", system_out); gsub(/\t/, "\\t", system_out);
        }

        # 打印用于Bulk API的action元数据行
        printf "{\"index\":{}}\n";

        # 打印数据行
        printf "{";
        printf "\"timestamp\":\"%s\",", strftime("%Y-%m-%dT%H:%M:%SZ", systime());
        printf "\"job_id\":\"local-run-%s\",", ENVIRON["GITHUB_RUN_ID"] ? ENVIRON["GITHUB_RUN_ID"] : "manual";
        printf "\"test_suite\":\"%s\",", class_name;
        printf "\"test_case\":\"%s\",", test_case_name;
        printf "\"duration_sec\":%f,", time_as_number;
        printf "\"status\":\"%s\",", status;
        printf "\"error_message\":\"%s\",", error_message;
        printf "\"system_out\":\"%s\"", system_out;
        printf "}\n";
    }
    ' "$file")

    # 关键修正：使用 printf "%s\n" 来确保我们发送的数据块最后一定有一个换行符。
    # 这将彻底解决 "The bulk request must be terminated by a newline" 的问题。
    printf "%s\n" "$JSON_PAYLOAD" | curl -s -X POST "$OPENSEARCH_URL/$INDEX_NAME/_bulk" -H 'Content-Type: application/x-ndjson' --data-binary @-

    echo "  - 已处理并上传报告: $file"
done

echo ""
print_header "✅ 数据上报完成"
