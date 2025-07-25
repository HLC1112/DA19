// 文件路径: scripts/analyze-and-report-failure.js
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// 我们本地 OpenSearch 的地址
const OPENSEARCH_URL = 'http://host.docker.internal:9200';

/**
 * 模拟调用 AI 进行根本原因分析
 * @param {string} context - 提供给 AI 的上下文信息
 * @returns {Promise<string>} - AI 生成的分析报告
 */
async function analyzeWithAI(context) {
    console.log("--- Calling AI for Root Cause Analysis ---");
    // 在真实场景中，这里会是一个 fetch 调用，将 context 发送给 Gemini API
    // const response = await fetch('https://gemini.api.google.com/...', { ... });
    // const analysis = await response.json();

    // 为了本地演示，我们直接使用您提供的分析模板
    const analysisText = `## 构建失败根本原因分析及修复建议\n\n根据提供的构建错误日志和 Git 提交上下文，构建失败的根本原因是**测试用例 \`Da19ApplicationTests.kt\` 中的 \`a_simple_test_that_is_guaranteed_to_fail()\` 方法断言失败**...（省略）...通过仔细检查代码逻辑并修正错误，即可解决问题。`;
    return analysisText;
}

/**
 * 将 JSON 数据上传到 OpenSearch
 * @param {string} index - OpenSearch 的索引名称
 * @param {object} data - 要上传的 JSON 对象
 */
async function uploadJson(index, data) {
    try {
        const response = await fetch(`${OPENSEARCH_URL}/${index}/_doc`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data),
        });
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        console.log(`Successfully uploaded data to index [${index}]`);
    } catch (error) {
        console.error(`Failed to upload data to index [${index}]:`, error);
    }
}

/**
 * 主函数
 */
async function main() {
    // 从环境变量中检查上一步（测试）是否失败
    const testStepOutcome = process.env.TEST_STEP_OUTCOME;
    if (testStepOutcome !== 'failure') {
        console.log('Tests passed. No analysis needed.');
        return;
    }

    console.log('Tests failed. Starting analysis and reporting process...');

    // 1. 收集上下文信息
    const buildLog = fs.readFileSync('build_log.txt', 'utf8');

    // 提取关键错误信息 (这是一个简化的示例)
    const errorLocation = buildLog.split('\n').find(line => line.includes('FAILED'))?.trim() || 'Unknown location';

    // 2. 调用 AI 进行分析
    const aiAnalysis = await analyzeWithAI(buildLog);

    // 3. 组装最终的报告
    const report = {
        timestamp: new Date().toISOString(),
        project: "greenbloo/0722DA12.v1", // 示例
        branch: "master", // 示例
        committer: "nektos/act", // 示例
        error_location: errorLocation,
        ai_analysis: aiAnalysis,
        full_log: buildLog, // 将完整日志也一并上报
        status: 'FAILURE'
    };

    // 4. 上报到 OpenSearch
    await uploadJson('ci-failures', report);
}

main();
