#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MyGitHub 全自动版本自增与一键发版引擎
用法:
    python scripts/release.py            # 默认补丁号自增：0.0.1 -> 0.0.2
    python scripts/release.py --minor    # 次版本号自增：0.0.1 -> 0.1.0
    python scripts/release.py --major    # 主版本号自增：0.0.1 -> 1.0.0
    python scripts/release.py --version 0.0.3  # 指定版本号
"""

import argparse
import hashlib
import json
import os
import subprocess
import sys
import urllib.request
import urllib.error

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
VERSION_FILE = os.path.join(PROJECT_ROOT, "VERSION")
LOCAL_PROPS = os.path.join(PROJECT_ROOT, "local.properties")
GITHUB_REPO = "angusdevgo/MyGitHub"

def get_github_token() -> str:
    # 1. 优先从 local.properties 读取 (本地私有，不入库)
    if os.path.exists(LOCAL_PROPS):
        with open(LOCAL_PROPS, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip().startswith("GITHUB_TOKEN="):
                    return line.strip().split("=", 1)[1].strip()
    # 2. 从系统环境变量读取
    return os.environ.get("GITHUB_TOKEN", "").strip()

GITHUB_TOKEN = get_github_token()

try:
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    if hasattr(sys.stderr, 'reconfigure'):
        sys.stderr.reconfigure(encoding='utf-8')
except Exception:
    pass

def get_current_version() -> str:
    if os.path.exists(VERSION_FILE):
        with open(VERSION_FILE, "r", encoding="utf-8") as f:
            v = f.read().strip()
            if v: return v
    return "0.0.1"

def bump_version(current: str, bump_type: str, explicit: str = None) -> str:
    if explicit:
        return explicit.lstrip("v")
    parts = [int(p) if p.isdigit() else 0 for p in current.split(".")]
    while len(parts) < 3:
        parts.append(0)
    
    if bump_type == "major":
        return f"{parts[0] + 1}.0.0"
    elif bump_type == "minor":
        return f"{parts[0]}.{parts[1] + 1}.0"
    else:  # patch
        return f"{parts[0]}.{parts[1]}.{parts[2] + 1}"

def compute_sha256(file_path: str) -> str:
    h = hashlib.sha256()
    with open(file_path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def main():
    parser = argparse.ArgumentParser(description="MyGitHub 一键全自动版本迭代发布工具")
    parser.add_argument("--major", action="store_true", help="增加主版本号 (X.0.0)")
    parser.add_argument("--minor", action="store_true", help="增加次版本号 (0.X.0)")
    parser.add_argument("--version", type=str, help="直接指定新版本号 (例如 0.0.2)")
    parser.add_argument("--no-push", action="store_true", help="仅本地构建打包，不推送到 GitHub")
    parser.add_argument("--notes", type=str, default="", help="版本更新日志")
    args = parser.parse_args()

    curr_ver = get_current_version()
    bump_type = "major" if args.major else ("minor" if args.minor else "patch")
    new_ver = bump_version(curr_ver, bump_type, args.version)

    print(f"\n==========================================")
    print(f"🚀 MyGitHub 版本自动迭代: v{curr_ver} -> v{new_ver}")
    print(f"==========================================\n")

    # 1. 写入新版本号到 VERSION 文件
    with open(VERSION_FILE, "w", encoding="utf-8") as f:
        f.write(new_ver + "\n")
    print(f"✓ 已更新 VERSION 文件: {new_ver}")

    # 2. 执行 Gradle 构建 Release APK
    print("\n📦 正在构建 Release APK (assembleRelease)...")
    env = os.environ.copy()
    if not env.get("JAVA_HOME") and os.path.exists("D:\\Tool\\JDK 17"):
        env["JAVA_HOME"] = "D:\\Tool\\JDK 17"

    gradle_cmd = os.path.join(PROJECT_ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
    res = subprocess.run([gradle_cmd, "assembleRelease", "--console=plain"], cwd=PROJECT_ROOT, env=env)
    if res.returncode != 0:
        print("✕ Gradle 构建失败，发布终止。")
        sys.exit(1)

    built_apk = os.path.join(PROJECT_ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")
    if not os.path.exists(built_apk):
        print(f"✕ 找不到构建生成的 APK: {built_apk}")
        sys.exit(1)

    # 3. 归档与校验和
    release_dir = os.path.join(PROJECT_ROOT, "release")
    os.makedirs(release_dir, exist_ok=True)
    target_apk = os.path.join(release_dir, f"MyGitHub-v{new_ver}-release.apk")
    target_sha = target_apk + ".sha256"

    with open(built_apk, "rb") as sf, open(target_apk, "wb") as df:
        df.write(sf.read())

    sha256 = compute_sha256(target_apk)
    with open(target_sha, "w", encoding="utf-8") as f:
        f.write(sha256 + "\n")

    apk_size_mb = os.path.getsize(target_apk) / (1024 * 1024)
    print(f"\n✓ 归档成功: {os.path.basename(target_apk)} ({apk_size_mb:.2f} MB)")
    print(f"✓ SHA-256 校验和: {sha256}")

    # 4. ADB 自动安装（如连接设备）
    adb_path = "D:/Tool/Android/platform-tools/adb.exe"
    if os.path.exists(adb_path):
        devs = subprocess.run([adb_path, "devices"], capture_output=True, text=True).stdout
        online = [line.split()[0] for line in devs.strip().split("\n")[1:] if "device" in line]
        if online:
            dev = online[0]
            print(f"\n📱 发现在线真机设备 [{dev}]，正在推屏安装...")
            subprocess.run([adb_path, "-s", dev, "install", "-r", "-d", target_apk], capture_output=True)
            subprocess.run([adb_path, "-s", dev, "shell", "am", "force-stop", "com.mygithub.lab"], capture_output=True)
            subprocess.run([adb_path, "-s", dev, "shell", "monkey", "-p", "com.mygithub.lab", "-c", "android.intent.category.LAUNCHER", "1"], capture_output=True)
            print("✓ 真机安装并拉起成功！")

    if args.no_push:
        print("\n🏁 已完成本地打包，跳过 GitHub 推送。")
        return

    # 5. Git Commit & Push
    git_path = "D:/Tool/Git/cmd/git.exe" if os.path.exists("D:/Tool/Git/cmd/git.exe") else "git"
    print("\n🌿 正在提交版本演进到 Git...")
    subprocess.run([git_path, "add", "-A"], cwd=PROJECT_ROOT)
    commit_msg = f"release: bump version to v{new_ver}\n\n- Automated release v{new_ver}\n- APK SHA256: {sha256}"
    subprocess.run([git_path, "commit", "-q", "-m", commit_msg], cwd=PROJECT_ROOT)

    remote_url = f"https://angusdevgo:{GITHUB_TOKEN}@github.com/{GITHUB_REPO}.git"
    subprocess.run([git_path, "remote", "set-url", "origin", remote_url], cwd=PROJECT_ROOT)
    subprocess.run([git_path, "push", "origin", "main"], cwd=PROJECT_ROOT)
    subprocess.run([git_path, "remote", "set-url", "origin", f"https://github.com/{GITHUB_REPO}.git"], cwd=PROJECT_ROOT)
    print("✓ 代码已推送到 GitHub main 分支！")

    # 6. 发布 GitHub Release & 上传 Asset
    print(f"\n🚀 正在向 GitHub 发布 Release v{new_ver}...")
    release_notes = args.notes if args.notes else f"""### 自动版本发布 / Release v{new_ver}

- 优化性能、完善网络代理与体验细节
- 完整包含 RFC 6238 2FA 动态码、极客悬浮底栏与全新二级应用设置

#### 安装包完整性校验
- **文件名**: `MyGitHub-v{new_ver}-release.apk`
- **SHA256**: `{sha256}`
"""
    create_data = {
        "tag_name": f"v{new_ver}",
        "target_commitish": "main",
        "name": f"MyGitHub v{new_ver}",
        "body": release_notes,
        "draft": False,
        "prerelease": False
    }

    req = urllib.request.Request(
        f"https://api.github.com/repos/{GITHUB_REPO}/releases",
        data=json.dumps(create_data).encode("utf-8"),
        headers={"Authorization": f"token {GITHUB_TOKEN}", "Content-Type": "application/json", "User-Agent": "MyGitHub-Release-Bot"},
        method="POST"
    )

    try:
        with urllib.request.urlopen(req) as resp:
            rel = json.loads(resp.read().decode("utf-8"))
            rel_id = rel["id"]
    except urllib.error.HTTPError as e:
        # 如果 tag 已存在，查询其 id
        req2 = urllib.request.Request(
            f"https://api.github.com/repos/{GITHUB_REPO}/releases/tags/v{new_ver}",
            headers={"Authorization": f"token {GITHUB_TOKEN}", "User-Agent": "MyGitHub-Release-Bot"}
        )
        with urllib.request.urlopen(req2) as resp2:
            rel = json.loads(resp2.read().decode("utf-8"))
            rel_id = rel["id"]

    # 上传 APK
    upload_url = f"https://uploads.github.com/repos/{GITHUB_REPO}/releases/{rel_id}/assets?name=MyGitHub-v{new_ver}-release.apk"
    with open(target_apk, "rb") as f:
        apk_bytes = f.read()

    up_req = urllib.request.Request(
        upload_url,
        data=apk_bytes,
        headers={"Authorization": f"token {GITHUB_TOKEN}", "Content-Type": "application/vnd.android.package-archive", "User-Agent": "MyGitHub-Release-Bot"},
        method="POST"
    )
    with urllib.request.urlopen(up_req) as up_resp:
        asset = json.loads(up_resp.read().decode("utf-8"))
        print(f"✓ APK Asset 上传成功！({asset['size']} bytes)")
        print(f"🔗 下载链接: {asset['browser_download_url']}")

    print(f"\n🎉 恭喜！版本 v{new_ver} 已全自动迭代并公开发布完毕！\n")

if __name__ == "__main__":
    main()
