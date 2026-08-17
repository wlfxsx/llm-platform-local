# SQLite 向量扩展

按操作系统将 Vec1 或 sqlite-vec 动态库放到安装目录的 `native/`：

- Windows：`vec0.dll`
- Linux：`vec0.so` 或 `libvec0.so`
- macOS：`vec0.dylib` 或 `libvec0.dylib`

启动时后端会尝试加载；失败则使用 BLOB + 暴力检索，应用仍可启动。不要把扩展文件放到 `.me/`。
