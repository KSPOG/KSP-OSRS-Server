# Revision-242 cache inventory (metadata-only)

This command-line utility inventories **filenames and sizes only** for conventional `main_file_cache.dat2` and `main_file_cache.idxN` files from a user-selected local cache directory. It does **not** copy or decode cache assets, list account data, or prove that the cache matches OSRS revision 242.

Requires Python 3 (no third-party dependencies). On Windows:

```powershell
python .\tools\cache_inventory.py "D:\path\to\your\cache\folder" --output ".\cache-inventory.json"
```

Review the generated `cache-inventory.json` before sharing. Do not upload the entire game cache, credentials, or account directories. Report only confirms recognized file structure; to serve a matching JS5 cache, an archive/group reader and revision-specific index validation are still necessary.

The tool deliberately does not guess your cache path or scan your machine automatically.
