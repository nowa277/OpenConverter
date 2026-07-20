const fs = require('fs');
const os = require('os');
const path = require('path');
const { exec } = require('node:child_process');

const CSHARP_SCRIPT = `
using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

public class QMEx {
    [DllImport("kernel32.dll")] static extern IntPtr OpenProcess(int a,bool b,int p);
    [DllImport("kernel32.dll")] static extern bool ReadProcessMemory(IntPtr h,IntPtr a,byte[] b,int s,out int r);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr h);
    [DllImport("kernel32.dll")] static extern int VirtualQueryEx(IntPtr h,IntPtr a,out MBI m,int l);
    [StructLayout(LayoutKind.Sequential)] public struct MBI {
        public IntPtr BaseAddress,AllocationBase; public uint AllocationProtect;
        public IntPtr RegionSize; public uint State,Protect,Type;
    }
    static string ExtractCookieLine(string line) {
        string clean = line.Trim();
        if (clean.StartsWith("Cookie:")) clean = clean.Substring(7).Trim();
        if (clean.StartsWith("?")) clean = clean.Substring(1).Trim();
        if (clean.Contains("qqmusic_key=") || clean.Contains("qm_keyst=")) return clean;
        return "";
    }
    static string ExtractValue(string line, string key) {
        int start = line.IndexOf(key);
        if (start < 0) return "";
        start += key.Length;
        int end = line.IndexOf(';', start);
        if (end < 0) end = line.IndexOf('&', start);
        if (end < 0) end = line.Length;
        return line.Substring(start, end - start).Trim();
    }
    static string JsonEscape(string s) {
        return s.Replace("\\\\", "\\\\\\\\").Replace("\\\"", "\\\\\\\"").Replace("\\r", "").Replace("\\n", "");
    }
    public static string Run() {
        string[] processNames = { "QQMusic", "qmbrowser" };
        
        string[] targets = { "qqmusic_key=", "qm_keyst=", "qqmusic_guid=" };
        byte[][] markers = new byte[targets.Length][];
        for(int i=0; i<targets.Length; i++) markers[i] = Encoding.ASCII.GetBytes(targets[i]);

        string foundCookie = "";
        string foundGuid = "";
        string foundUin = "";
        bool sawProcess = false;

        foreach (var processName in processNames) foreach (var p in Process.GetProcessesByName(processName)) {
            sawProcess = true;
            IntPtr h=OpenProcess(0x0410,false,p.Id);
            if(h==IntPtr.Zero) continue;
            try {
                IntPtr addr=IntPtr.Zero; MBI mbi;
                while(VirtualQueryEx(h,addr,out mbi,Marshal.SizeOf(typeof(MBI)))!=0) {
                    long sz=mbi.RegionSize.ToInt64();
                    if(mbi.State==0x1000 && sz>0 && sz<50*1024*1024) {
                        uint p2=mbi.Protect&0xFF;
                        if(p2==2||p2==4||p2==6||p2==0x20||p2==0x40||p2==0x60||p2==0x80) {
                            byte[] buf=new byte[sz]; int rd;
                            if(ReadProcessMemory(h,mbi.BaseAddress,buf,buf.Length,out rd)) {
                                foreach(var mk in markers) {
                                    for(int i=0;i<=rd-mk.Length;i++) {
                                        bool ok=true;
                                        for(int j=0;j<mk.Length;j++) if(buf[i+j]!=mk[j]){ok=false;break;}
                                        if(ok) {
                                            int start = Math.Max(0, i - 1500);
                                            int end=Math.Min(i+1500,rd);
                                            string s = Encoding.UTF8.GetString(buf,start,end-start);
	                                            var lines = s.Split(new[] { '\\r', '\\n', '\\0' }, StringSplitOptions.RemoveEmptyEntries);
	                                            foreach(var line in lines) {
	                                                if (string.IsNullOrEmpty(foundCookie) && (line.Contains("qqmusic_key=") || line.Contains("qm_keyst="))) {
	                                                    foundCookie = ExtractCookieLine(line);
	                                                }
	                                                if (string.IsNullOrEmpty(foundGuid) && line.Contains("qqmusic_guid=")) {
	                                                    foundGuid = ExtractValue(line, "qqmusic_guid=");
	                                                }
	                                                if (string.IsNullOrEmpty(foundUin)) {
	                                                    foundUin = ExtractValue(line, "qqmusic_uin=");
	                                                    if (string.IsNullOrEmpty(foundUin)) foundUin = ExtractValue(line, "qm_hideuin=");
	                                                    if (string.IsNullOrEmpty(foundUin)) foundUin = ExtractValue(line, "uin=");
	                                                    if (string.IsNullOrEmpty(foundUin)) foundUin = ExtractValue(line, "uid=");
	                                                }
	                                                if (!string.IsNullOrEmpty(foundCookie) && !string.IsNullOrEmpty(foundGuid) && !string.IsNullOrEmpty(foundUin)) {
	                                                    return "{\\\"cookie\\\":\\\"" + JsonEscape(foundCookie) + "\\\",\\\"guid\\\":\\\"" + JsonEscape(foundGuid) + "\\\",\\\"uin\\\":\\\"" + JsonEscape(foundUin) + "\\\"}";
	                                                }
	                                            }
	                                        }
                                    }
                                }
                            }
                        }
                    }
                    long next = addr.ToInt64() + sz;
                    if (next <= addr.ToInt64()) break;
                    addr = new IntPtr(next);
                }
            } finally {
	                CloseHandle(h);
	            }
	        }
        if(!sawProcess) return "ERROR:not_running";
        if(!string.IsNullOrEmpty(foundCookie) && !string.IsNullOrEmpty(foundGuid)) {
            return "{\\\"cookie\\\":\\\"" + JsonEscape(foundCookie) + "\\\",\\\"guid\\\":\\\"" + JsonEscape(foundGuid) + "\\\",\\\"uin\\\":\\\"" + JsonEscape(foundUin) + "\\\"}";
        }
        return "ERROR:not_found";
    }
}
`;

function extractCookie() {
  return new Promise((resolve) => {
    if (process.platform !== 'win32') {
      return resolve({ ok: false, error: 'Memory scanning is only supported on Windows.' });
    }

    const psScript = `
Add-Type -TypeDefinition @"
${CSHARP_SCRIPT}
"@
Write-Output ([QMEx]::Run())
`;

    const tmpPs1 = path.join(os.tmpdir(), `qm_scan_${Date.now()}.ps1`);
    fs.writeFileSync(tmpPs1, psScript);

    exec(`powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "${tmpPs1}"`, { maxBuffer: 1024 * 1024 }, (err, stdout, stderr) => {
      try { fs.unlinkSync(tmpPs1); } catch {}

      if (err) {
        return resolve({ ok: false, error: 'PowerShell execution failed: ' + (stderr || err.message).trim() });
      }
      if (stderr) console.log("DEBUG STDERR:", stderr);

      const text = stdout.trim();
      if (!text || text === 'ERROR:not_running') {
        return resolve({ ok: false, error: 'QQ Music is not running. Please start QQ Music first.' });
      }
      if (text.startsWith('ERROR:')) {
        if (text === 'ERROR:not_found') {
          return resolve({ ok: false, error: 'VIP cookie not found in memory. Please ensure you are logged into a VIP account and try playing a VIP song in QQ Music, then try again.' });
        }
        return resolve({ ok: false, error: text });
      }

      try {
        const data = JSON.parse(text);
        
        let uin = data.uin || '';
        const uinMatch = data.cookie.match(/qqmusic_uin=o?(\\d+)/) || data.cookie.match(/(?:^|;\\s*)uin=o?(\\d+)/) || data.cookie.match(/qm_hideuin=o?(\\d+)/) || data.cookie.match(/uid=o?(\\d+)/) || data.cookie.match(/ptui_loginuin=o?([^;]+)/);
        if (uinMatch) uin = uinMatch[1].replace(/^o0*/, '');
        uin = String(uin).replace(/^o?0*/, '');
        if (!uin) return resolve({ ok: false, error: 'UIN not found in cookie.' });

        resolve({ ok: true, cookie: data.cookie, guid: data.guid, uin });
      } catch (e) {
        resolve({ ok: true, cookie: text });
      }
    });
  });
}

module.exports = { extractCookie };
