const { execSync } = require('child_process');
const os = require('os');
const fs = require('fs');
const path = require('path');

console.log('开始环境检测...');

// 1. 检测 Rust 工具链
try {
    const rustVersion = execSync('rustc --version').toString().trim();
    console.log(`检测到 Rust: ${rustVersion}`);
    
    // 简单版本检查逻辑 (假设 rustc 1.70.0)
    const versionMatch = rustVersion.match(/rustc (\d+)\.(\d+)/);
    if (versionMatch) {
        const major = parseInt(versionMatch[1]);
        const minor = parseInt(versionMatch[2]);
        if (major < 1 || (major === 1 && minor < 70)) {
            console.error('Rust 版本过低，请升级至 1.70+');
            process.exit(1);
        }
    }
} catch (e) {
    console.log('未检测到 Rust，尝试自动安装...');
    // 注意：自动安装通常需要交互，这里仅作为演示提示
    console.log('请访问 https://sh.rustup.rs 安装 Rust');
    process.exit(1);
}

// 2. 检测 VS C++ Build Tools (Windows Only)
if (os.platform() === 'win32') {
    console.log('正在检测 Windows 构建工具...');
    let found = false;

    // 方法 A: 使用 vswhere (最可靠)
    try {
        const vswherePath = path.join(process.env['ProgramFiles(x86)'] || process.env.ProgramFiles, 'Microsoft Visual Studio', 'Installer', 'vswhere.exe');
        if (fs.existsSync(vswherePath)) {
            const output = execSync(`"${vswherePath}" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`).toString().trim();
            if (output) {
                console.log(`通过 vswhere 检测到 VS Build Tools: ${output}`);
                found = true;
            }
        }
    } catch (e) {
        // vswhere 失败，忽略
    }

    // 方法 B: 检查常见安装路径 (如果 vswhere 失败)
    if (!found) {
        const commonPaths = [
            'C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\BuildTools',
            'C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools',
            'C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\BuildTools',
            'C:\\Program Files\\Microsoft Visual Studio\\2022\\Community',
            'C:\\Program Files\\Microsoft Visual Studio\\2022\\Enterprise',
            'C:\\Program Files\\Microsoft Visual Studio\\2022\\Professional'
        ];

        for (const p of commonPaths) {
            if (fs.existsSync(p)) {
                console.log(`通过路径检测到 VS Build Tools: ${p}`);
                found = true;
                break;
            }
        }
    }
    
    // 方法 C: 检查 cl.exe 是否在 PATH 中 (最宽松)
    if (!found) {
        try {
            execSync('cl', { stdio: 'ignore' }); // cl 运行时通常会输出版本信息然后退出，或者报错需要输入文件
            // 如果没报错(或者报的是编译错误的错而非找不到命令)，说明在 PATH 里
            // 但 execSync 如果返回非0会抛出异常。cl 不带参数会返回非0吗？
            // cl 不带参数通常输出用法并返回非0。所以这里 catch 异常是预期的。
            // 我们检查 error.code 是否是 ENOENT
        } catch (e) {
            if (e.code !== 'ENOENT') {
                console.log('通过 PATH 检测到 cl.exe (MSVC)');
                found = true;
            }
        }
    }

    if (!found) {
        console.warn('警告: 脚本未能自动检测到 VS C++ Build Tools。');
        console.warn('如果您确认已安装 "使用 C++ 的桌面开发" 和 "MSVC v143" 组件，请忽略此警告。');
        console.warn('若后续 "npm run tauri dev" 失败，请尝试重新安装或重启电脑。');
        // 不再强制退出，因为注册表检测可能不准
        // process.exit(1); 
    } else {
        console.log('VS C++ Build Tools 检测通过。');
    }
}

console.log('环境检测通过！');
