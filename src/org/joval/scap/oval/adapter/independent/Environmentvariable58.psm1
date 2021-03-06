# Copyright (C) 2012 jOVAL.org.  All rights reserved.
# This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt
#
function Get-ProcessEnvironment {
  param(
    [int]$ProcessId=$(throw "Mandatory parameter -ProcessId missing.")
  )

  $code = @"
using System;
using System.Collections.Generic;
using System.Text;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Runtime.CompilerServices;
using System.Collections.Specialized;

namespace jOVAL.Environment58 {
    public class Probe {
	public const int PAGE_NOACCESS = 0x01;
	public const int PAGE_EXECUTE = 0x10;
	public const int ProcessBasicInformation = 0;
	public const int ProcessWow64Information = 26;

	[Flags]
	enum ProcessAccessFlags : uint {
	    All = 0x001F0FFF,
	    Terminate = 0x00000001,
	    CreateThread = 0x00000002,
	    VMOperation = 0x00000008,
	    VMRead = 0x00000010,
	    VMWrite = 0x00000020,
	    DupHandle = 0x00000040,
	    SetInformation = 0x00000200,
	    QueryInformation = 0x00000400,
	    Synchronize = 0x00100000
	}

	[StructLayout(LayoutKind.Sequential, Pack = 1)]
	public struct PROCESS_BASIC_INFORMATION
	{
	    public IntPtr Reserved1;
	    public IntPtr PebBaseAddress;
	    [MarshalAs(UnmanagedType.ByValArray, SizeConst = 2)]
	    public IntPtr[] Reserved2;
	    public IntPtr UniqueProcessId;
	    public IntPtr Reserved3;
	}

	[StructLayout(LayoutKind.Sequential)]
	public struct MEMORY_BASIC_INFORMATION {
	    public IntPtr BaseAddress;
	    public IntPtr AllocationBase;
	    public int AllocationProtect;
	    public IntPtr RegionSize;
	    public int State;
	    public int Protect;
	    public int Type;
	}

	[StructLayout(LayoutKind.Sequential, Size=40)]
	public struct PROCESS_MEMORY_COUNTERS {
	    public uint cb;
	    public uint PageFaultCount;
	    public IntPtr PeakWorkingSetSize;
	    public IntPtr WorkingSetSize;
	    public IntPtr QuotaPeakPagedPoolUsage;
	    public IntPtr QuotaPagedPoolUsage;
	    public IntPtr QuotaPeakNonPagedPoolUsage;
	    public IntPtr QuotaNonPagedPoolUsage;
	    public IntPtr PagefileUsage;
	    public IntPtr PeakPagefileUsage;
	}

	[DllImport("psapi.dll", SetLastError=true)]
	extern static bool GetProcessMemoryInfo(IntPtr hProcess, out PROCESS_MEMORY_COUNTERS counters, out uint size);

	[DllImport("kernel32.dll")]
	static extern IntPtr OpenProcess(ProcessAccessFlags dwDesiredAccess, bool bInheritHandle, int dwProcessId);

	[DllImport("kernel32.dll", SetLastError=true)]
	[return: MarshalAs(UnmanagedType.Bool)]
	static extern bool CloseHandle(IntPtr hObject);

	[DllImport("kernel32.dll")]
	extern static int GetLastError();

	[DllImport("ntdll.dll", SetLastError = true)]
	public static extern int NtQueryInformationProcess(IntPtr hProcess, int pic, ref PROCESS_BASIC_INFORMATION pbi, int cb, ref int pSize);

	[DllImport("ntdll.dll", SetLastError = true)]
	public static extern int NtQueryInformationProcess(IntPtr hProcess, int pic, ref IntPtr pi, int cb, ref int pSize);

	[DllImport("kernel32.dll", SetLastError = true)]
	public static extern bool ReadProcessMemory(IntPtr hProcess, IntPtr lpBaseAddress, [Out] byte[] lpBuffer, IntPtr dwSize, ref IntPtr lpNumberOfBytesRead);

	[DllImport("kernel32.dll", SetLastError = true)]
	public static extern bool ReadProcessMemory(IntPtr hProcess, IntPtr lpBaseAddress, IntPtr lpBuffer, IntPtr dwSize, ref IntPtr lpNumberOfBytesRead);

	[DllImport("kernel32", SetLastError = true)]
	public static extern int VirtualQueryEx(IntPtr hProcess, IntPtr lpAddress, ref MEMORY_BASIC_INFORMATION lpBuffer, int dwLength);

	[DllImport("kernel32.dll", SetLastError = true)]
	public static extern bool IsWow64Process(IntPtr hProcess, out bool wow64Process);

	public static StringDictionary GetEnvironmentVariables(UInt32 pid) {
	    ProcessAccessFlags flags = ProcessAccessFlags.QueryInformation | ProcessAccessFlags.VMRead;
	    IntPtr hProcess = IntPtr.Zero;
	    hProcess = OpenProcess(flags, false, (int)pid);
	    if (hProcess == IntPtr.Zero) {
		throw new System.ComponentModel.Win32Exception(GetLastError());
	    }

	    IntPtr penv = _GetPenv(hProcess);
	    try {
		uint size;
		PROCESS_MEMORY_COUNTERS pmc = new PROCESS_MEMORY_COUNTERS();
		if (!GetProcessMemoryInfo(hProcess, out pmc, out size)) {
		    throw new System.ComponentModel.Win32Exception(GetLastError());
		}
		int dataSize = (int)pmc.WorkingSetSize;
		const int maxEnvSize = 32767;
		if (dataSize > maxEnvSize) {
		    dataSize = maxEnvSize;
		}
		byte[] envData = new byte[dataSize];
		IntPtr res_len = IntPtr.Zero;
		bool b = ReadProcessMemory(hProcess, penv, envData, new IntPtr(dataSize), ref res_len);
		if (!b || (int)res_len != dataSize) {
		    throw new System.ComponentModel.Win32Exception(GetLastError());
		}
		return _EnvToDictionary(envData);
	    } finally {
		CloseHandle(hProcess);
	    }
	}

	static StringDictionary _EnvToDictionary(byte[] env) {
	    StringDictionary result = new StringDictionary();

	    int len = env.Length;
	    if (len < 4) {
		return result;
	    }

	    int n = len - 3;
	    for (int i=0; i < n; ++i) {
		byte c1 = env[i];
		byte c2 = env[i + 1];
		byte c3 = env[i + 2];
		byte c4 = env[i + 3];

		if (c1 == 0 && c2 == 0 && c3 == 0 && c4 == 0) {
		    len = i + 3;
		    break;
		}
	    }

	    char[] environmentCharArray = Encoding.Unicode.GetChars(env, 0, len);

	    for (int i=0; i < environmentCharArray.Length; i++) {
		int startIndex = i;
		while ((environmentCharArray[i] != '=') && (environmentCharArray[i] != '\0') && (i < environmentCharArray.Length)) {
		    i++;
		}
		if (environmentCharArray[i] != '\0') {
		    if ((i - startIndex) == 0) {
			while (environmentCharArray[i] != '\0' && i < environmentCharArray.Length) {
			    i++;
			}
		    } else {
			string str = new string(environmentCharArray, startIndex, i - startIndex);
			if (i < environmentCharArray.Length) {
			    i++;
			    int num3 = i;
			    while (environmentCharArray[i] != '\0' && i < environmentCharArray.Length) {
				i++;
			    }
			    string str2 = new string(environmentCharArray, num3, i - num3);
			    result[str] = str2;
			} else {
			    result[str] = "";
			}
		    }
		}
	    }
	    return result;
	}

	static bool _TryReadIntPtr32(IntPtr hProcess, IntPtr ptr, out IntPtr readPtr) {
	    bool result;
	    RuntimeHelpers.PrepareConstrainedRegions();
	    try {
	    } finally {
		int dataSize = sizeof(Int32);
		IntPtr data = Marshal.AllocHGlobal(dataSize);
		IntPtr res_len = IntPtr.Zero;
		bool b = ReadProcessMemory(hProcess, ptr, data, new IntPtr(dataSize), ref res_len);
		readPtr = new IntPtr(Marshal.ReadInt32(data));
		Marshal.FreeHGlobal(data);
		if (!b || (int)res_len != dataSize) {
		    result = false;
		} else {
		    result = true;
		}
	    }
	    return result;
	}

	static bool _TryReadIntPtr(IntPtr hProcess, IntPtr ptr, out IntPtr readPtr) {
	    bool result;
	    RuntimeHelpers.PrepareConstrainedRegions();
	    try {
	    } finally {
		int dataSize = IntPtr.Size;
		IntPtr data = Marshal.AllocHGlobal(dataSize);
		IntPtr res_len = IntPtr.Zero;
		bool b = ReadProcessMemory(hProcess, ptr, data, new IntPtr(dataSize), ref res_len);
		readPtr = Marshal.ReadIntPtr(data);
		Marshal.FreeHGlobal(data);
		if (!b || (int)res_len != dataSize) {
		    result = false;
		} else {
		    result = true;
		}
	    }
	    return result;
	}

	static IntPtr _GetPenv(IntPtr hProcess) {
	    int processBitness = _GetProcessBitness(hProcess);

	    if (processBitness == 64) {
		if (IntPtr.Size != 8) {
		    throw new InvalidOperationException("The current process should run in 64 bit mode to be able to get the environment of another 64 bit process.");
		}
		IntPtr pPeb = _GetPeb64(hProcess);
		IntPtr ptr;
		if (!_TryReadIntPtr(hProcess, new IntPtr(pPeb.ToInt64() + 0x20), out ptr)) {
		    throw new Exception("Unable to read PEB.");
		}
		IntPtr penv;
		if (!_TryReadIntPtr(hProcess, new IntPtr(ptr.ToInt64() + 0x80), out penv)) {
		    throw new Exception("Unable to read RTL_USER_PROCESS_PARAMETERS.");
		}
		return penv;
	    } else {
		IntPtr pPeb = _GetPeb32(hProcess);
		IntPtr ptr;
		if (!_TryReadIntPtr32(hProcess, new IntPtr(pPeb.ToInt64() + 0x10), out ptr)) {
		    throw new Exception("Unable to read PEB.");
		}
		IntPtr penv;
		if (!_TryReadIntPtr32(hProcess, new IntPtr(ptr.ToInt64() + 0x48), out penv)) {
		    throw new Exception("Unable to read RTL_USER_PROCESS_PARAMETERS.");
		}

		return penv;
	    }
	}

	static int _GetProcessBitness(IntPtr hProcess) {
	    bool wow64;
	    if (!IsWow64Process(hProcess, out wow64)) {
		return 32;
	    }
	    if (wow64) {
		return 32;
	    } else {
		return 64;
	    }
	}

	static IntPtr _GetPeb32(IntPtr hProcess) {
	    if (IntPtr.Size == 8) {
		IntPtr ptr = IntPtr.Zero;
		int res_len = 0;
		int pbiSize = IntPtr.Size;
		int status = NtQueryInformationProcess(hProcess, ProcessWow64Information, ref ptr, pbiSize, ref res_len);
		if (res_len != pbiSize) {
		    throw new Exception("Unable to query process information.");
		}
		return ptr;
	    } else {
		return _GetPebNative(hProcess);
	    }
	}

	static IntPtr _GetPebNative(IntPtr hProcess) {
	    PROCESS_BASIC_INFORMATION pbi = new PROCESS_BASIC_INFORMATION();
	    int res_len = 0;
	    int pbiSize = Marshal.SizeOf(pbi);
	    int status = NtQueryInformationProcess(hProcess, ProcessBasicInformation, ref pbi, pbiSize, ref res_len);
	    if (res_len != pbiSize) {
		throw new Exception("Unable to query process information.");
	    }
	    return pbi.PebBaseAddress;
	}

	static IntPtr _GetPeb64(IntPtr hProcess) {
	    return _GetPebNative(hProcess);
	}
    }
}
"@

  $ErrorActionPreference = "SilentlyContinue"
  $type = [jOVAL.Environment58.Probe]

  $ErrorActionPreference = "Stop"
  if($type -eq $null){
    add-type $code
  }

  $ErrorActionPreference = "Continue"
  $dictionary = [jOVAL.Environment58.Probe]::GetEnvironmentVariables($ProcessId)
  foreach ($entry in $dictionary) {
    if ($entry -ne $null) {
      $var = $entry.Key.ToUpper()
      Write-Output "$($var)=$($entry.Value)"
    }
  }
}
