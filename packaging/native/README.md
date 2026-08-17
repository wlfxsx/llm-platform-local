# SQLite 向量扩展

SQLite 向量扩展是可选组件。按目标操作系统将 Vec1 或 sqlite-vec 动态库放到组装目录的 `native/`：

- Windows：`vec0.dll`
- Linux：`vec0.so` 或 `libvec0.so`
- macOS：`vec0.dylib` 或 `libvec0.dylib`

开发机上的二进制源文件应放在 `.me/files/`，需要组装时再复制到：

```text
.me/build/dist/llm-platform/native/
```

后端启动时会尝试加载扩展；扩展缺失或加载失败时，向量数据仍可使用 BLOB 存储和暴力检索，应用可以继续启动。

动态库不提交到仓库。
