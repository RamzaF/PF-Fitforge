#!/usr/bin/env python3
"""Stream a reviewed build allowlist; never transfer macOS xattrs or AppleDouble."""
import io
from pathlib import Path
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'InitializrSpringbootProject'
files = [ROOT / 'Dockerfile', ROOT / '.dockerignore', APP / 'pom.xml']
allowed = {'.java', '.properties', '.html', '.css', '.js', '.png', '.jpg', '.jpeg', '.webp', '.gif', '.svg', '.ico', '.woff', '.woff2', '.ttf'}
for path in sorted((APP / 'src').rglob('*')):
    if any(part.startswith('._') or part == '.DS_Store' for part in path.relative_to(ROOT).parts):
        continue
    if path.is_symlink():
        raise SystemExit(f'Refusing symlink in build context: {path.relative_to(ROOT)}')
    if path.is_file():
        if path.suffix.lower() not in allowed:
            raise SystemExit(f'Unreviewed file type in build context: {path.relative_to(ROOT)}')
        files.append(path)
with tempfile.TemporaryFile() as context:
    with tarfile.open(fileobj=context, mode='w', format=tarfile.USTAR_FORMAT) as archive:
        for path in files:
            data = path.read_bytes()
            info = tarfile.TarInfo(str(path.relative_to(ROOT)))
            info.size = len(data)
            info.mode = 0o644
            info.mtime = 1704067200  # Positive, fixed epoch: Maven skips resource copies at mtime=0.
            archive.addfile(info, io.BytesIO(data))
    print(f'Build context: {len(files)} allowlisted files, no xattrs, .git, dumps, logs or AppleDouble.', flush=True)
    for target, image in [('runtime', 'fitforge-demo-app:local'), ('test', 'fitforge-demo-test:local')]:
        context.seek(0)
        subprocess.run(['docker', 'build', '--progress=plain', '--target', target, '-t', image, '-'],
                       stdin=context, cwd=ROOT, check=True)
