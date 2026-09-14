package com.simple.jigou.core;

import com.simple.jigou.ai.model.HtmlCodeResult;
import com.simple.jigou.ai.model.MultiFileCodeResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class CodeParserTest {

    @Test
    void parseHtmlCode() {
        String codeContent = """
                请帮我生成一个简约的个人主页：
                ```html
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>个人主页</title>
                    <style>
                        body { font-family: Arial, sans-serif; background-color: #f4f4f4; }
                        .header { text-align: center; padding: 50px; }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <h1>张三的个人主页</h1>
                        <p>欢迎来到我的个人网站</p>
                    </div>
                </body>
                </html>
                ```
                以上是生成好的代码，请查收。
                """;
        HtmlCodeResult result = CodeParser.parseHtmlCode(codeContent);
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getHtmlCode());
    }

    @Test
    void parseMultiFileCode() {
        String codeContent = """
                请帮我生成一个简单的待办事项列表应用：
                ```html
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>待办事项</title>
                    <link rel="stylesheet" href="style.css">
                </head>
                <body>
                    <div class="app-container">
                        <h2>我的待办事项</h2>
                        <input type="text" id="taskInput" placeholder="输入新任务...">
                        <button id="addBtn">添加</button>
                        <ul id="taskList"></ul>
                    </div>
                    <script src="script.js"></script>
                </body>
                </html>
                ```
                ```css
                .app-container {
                    max-width: 400px;
                    margin: 0 auto;
                    padding: 20px;
                    background: #fff;
                    border-radius: 8px;
                }
                input {
                    padding: 8px;
                    width: 70%;
                }
                button {
                    padding: 8px 12px;
                }
                ```
                ```js
                document.getElementById('addBtn').addEventListener('click', function() {
                    const input = document.getElementById('taskInput');
                    const taskText = input.value.trim();
                    if (taskText === '') return;
                
                    const li = document.createElement('li');
                    li.textContent = taskText;
                    document.getElementById('taskList').appendChild(li);
                    input.value = '';
                });
                ```
                文件创建完成！
                """;
        MultiFileCodeResult result = CodeParser.parseMultiFileCode(codeContent);
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getHtmlCode());
        Assertions.assertNotNull(result.getCssCode());
        Assertions.assertNotNull(result.getJsCode());
    }
}