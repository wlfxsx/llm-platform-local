-- 8080 是本机最容易被其它程序（开发服务器、游戏客户端组件等）抢占的端口，改到不常见的 17891
UPDATE settings
SET value = replace(value, '"llamafilePort":8080', '"llamafilePort":17891')
WHERE key = 'app.settings'
  AND value LIKE '%"llamafilePort":8080%';
