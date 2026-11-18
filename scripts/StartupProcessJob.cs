using System;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using Microsoft.Win32.SafeHandles;

namespace Games4398
{
    // 启动器持有进程组句柄，窗口关闭后 Windows 会结束组内进程
    public sealed class StartupProcessJob : IDisposable
    {
        private const int ExtendedLimitInformation = 9;
        private const uint KillOnJobClose = 0x2000;
        private const uint UseStdHandles = 0x100;
        private const uint CreateNoWindow = 0x08000000;
        private const uint ExtendedStartupInfoPresent = 0x00080000;
        private const int StartupAttributeCount = 2;
        private const int StandardHandleCount = 3;
        private const uint ReadAccess = 0x80000000;
        private const uint ShareReadWrite = 3;
        private const uint OpenExisting = 3;
        private static readonly IntPtr HandleListAttribute = new IntPtr(0x00020002);
        private static readonly IntPtr JobListAttribute = new IntPtr(0x0002000D);
        private readonly SafeFileHandle job;

        // 创建进程组，并设置最后一个句柄关闭时终止组内进程
        public StartupProcessJob()
        {
            job = CreateJobObjectW(IntPtr.Zero, null);
            if (job.IsInvalid)
            {
                throw new Win32Exception();
            }

            ExtendedLimits limits = new ExtendedLimits();
            limits.Basic.LimitFlags = KillOnJobClose;
            if (!SetInformationJobObject(job, ExtendedLimitInformation, ref limits, Marshal.SizeOf(limits)))
            {
                Win32Exception error = new Win32Exception();
                job.Dispose();
                throw error;
            }
        }

        // 启动指定程序并把输出写入日志，返回用于等待进程退出的句柄
        public WaitHandle Start(string executable, string arguments, string workingDirectory,
                                string stdoutPath, string stderrPath)
        {
            using (SafeFileHandle input = OpenNullInput())
            using (FileStream output = OpenLog(stdoutPath))
            using (FileStream errorOutput = OpenLog(stderrPath))
            {
                IntPtr[] handles = new IntPtr[]
                {
                    input.DangerousGetHandle(),
                    output.SafeFileHandle.DangerousGetHandle(),
                    errorOutput.SafeFileHandle.DangerousGetHandle(),
                    job.DangerousGetHandle()
                };
                IntPtr size = IntPtr.Zero;
                InitializeProcThreadAttributeList(IntPtr.Zero, StartupAttributeCount, 0, ref size);
                if (size == IntPtr.Zero)
                {
                    throw new Win32Exception();
                }

                IntPtr attributes = Marshal.AllocHGlobal(size);
                IntPtr values = IntPtr.Zero;
                bool initialized = false;
                try
                {
                    values = Marshal.AllocHGlobal(handles.Length * IntPtr.Size);
                    Marshal.Copy(handles, 0, values, handles.Length);
                    initialized = InitializeProcThreadAttributeList(attributes, StartupAttributeCount, 0, ref size);
                    if (!initialized)
                    {
                        throw new Win32Exception();
                    }
                    SetStartupHandles(attributes, values);

                    StartupInfoEx startup = new StartupInfoEx();
                    startup.Attributes = attributes;
                    startup.Info.Size = Marshal.SizeOf(startup);
                    startup.Info.Flags = UseStdHandles;
                    startup.Info.StdInput = handles[0];
                    startup.Info.StdOutput = handles[1];
                    startup.Info.StdError = handles[2];
                    StringBuilder commandLine = new StringBuilder("\"" + executable + "\" " + arguments);
                    ProcessInfo process;
                    if (!CreateProcessW(executable, commandLine, IntPtr.Zero, IntPtr.Zero, true,
                                        CreateNoWindow | ExtendedStartupInfoPresent, IntPtr.Zero,
                                        workingDirectory, ref startup, out process))
                    {
                        throw new Win32Exception();
                    }

                    CloseHandle(process.Thread);
                    return new ProcessExitHandle(process.Process);
                }
                finally
                {
                    if (initialized)
                    {
                        DeleteProcThreadAttributeList(attributes);
                    }
                    Marshal.FreeHGlobal(values);
                    Marshal.FreeHGlobal(attributes);
                }
            }
        }

        // 创建进程时就指定标准输入输出和进程组，子进程从启动起便受进程组管理
        private static void SetStartupHandles(IntPtr attributes, IntPtr values)
        {
            int standardHandleBytes = StandardHandleCount * IntPtr.Size;
            IntPtr standardHandleSize = new IntPtr(standardHandleBytes);
            bool standardHandlesSet = UpdateProcThreadAttribute(attributes, 0, HandleListAttribute,
                values, standardHandleSize, IntPtr.Zero, IntPtr.Zero);
            if (!standardHandlesSet)
            {
                throw new Win32Exception();
            }

            IntPtr jobHandleValue = IntPtr.Add(values, standardHandleBytes);
            IntPtr jobHandleSize = new IntPtr(IntPtr.Size);
            bool jobHandleSet = UpdateProcThreadAttribute(attributes, 0, JobListAttribute,
                jobHandleValue, jobHandleSize, IntPtr.Zero, IntPtr.Zero);
            if (!jobHandleSet)
            {
                throw new Win32Exception();
            }
        }

        // 关闭进程组句柄，由 Windows 结束仍在运行的子进程
        public void Dispose()
        {
            job.Dispose();
        }

        // 将子进程的标准输入接到空设备，避免占用启动器的控制台输入
        private static SafeFileHandle OpenNullInput()
        {
            SecurityAttributes security = new SecurityAttributes();
            security.Size = Marshal.SizeOf(typeof(SecurityAttributes));
            security.InheritHandle = true;
            SafeFileHandle input = CreateFileW("NUL", ReadAccess, ShareReadWrite, ref security, OpenExisting, 0, IntPtr.Zero);
            if (input.IsInvalid)
            {
                throw new Win32Exception();
            }
            return input;
        }

        // 新建指定日志，允许子进程继承写入句柄，启动器同时可以读取
        private static FileStream OpenLog(string path)
        {
            return new FileStream(path, FileMode.Create, FileAccess.Write, FileShare.ReadWrite | FileShare.Inheritable);
        }

        // 把 Windows 进程句柄交给 .NET，调用方可以等待并释放它
        private sealed class ProcessExitHandle : WaitHandle
        {
            // 接管已启动进程的句柄，释放等待对象时同时关闭句柄
            public ProcessExitHandle(IntPtr handle)
            {
                SafeWaitHandle = new SafeWaitHandle(handle, true);
            }
        }

        // 进程组基本限制，字段顺序由 Windows 接口规定
        [StructLayout(LayoutKind.Sequential)]
        private struct BasicLimits
        {
            public long PerProcessTime, PerJobTime;
            public uint LimitFlags;
            public UIntPtr MinWorkingSet, MaxWorkingSet;
            public uint ActiveProcessLimit;
            public UIntPtr Affinity;
            public uint PriorityClass, SchedulingClass;
        }

        // Windows 进程组的扩展限制，包含基本限制和资源统计
        [StructLayout(LayoutKind.Sequential)]
        private struct ExtendedLimits
        {
            public BasicLimits Basic;
            public ulong ReadOperations, WriteOperations, OtherOperations;
            public ulong ReadBytes, WriteBytes, OtherBytes;
            public UIntPtr ProcessMemory, JobMemory, PeakProcessMemory, PeakJobMemory;
        }

        // 创建 Windows 句柄时用于指定是否允许子进程继承
        [StructLayout(LayoutKind.Sequential)]
        private struct SecurityAttributes
        {
            public int Size;
            public IntPtr SecurityDescriptor;
            [MarshalAs(UnmanagedType.Bool)]
            public bool InheritHandle;
        }

        // Windows 启动参数结构，包含窗口选项和标准输入输出句柄
        [StructLayout(LayoutKind.Sequential)]
        private struct StartupInfo
        {
            public int Size;
            public IntPtr Reserved, Desktop, Title;
            public uint X, Y, Width, Height, Columns, Rows, FillAttribute, Flags;
            public ushort ShowWindow, ReservedSize;
            public IntPtr ReservedBytes, StdInput, StdOutput, StdError;
        }

        // 在基本启动参数之外附加句柄与进程组属性
        [StructLayout(LayoutKind.Sequential)]
        private struct StartupInfoEx
        {
            public StartupInfo Info;
            public IntPtr Attributes;
        }

        // 接收 Windows 返回的进程和主线程句柄与编号
        [StructLayout(LayoutKind.Sequential)]
        private struct ProcessInfo
        {
            public IntPtr Process, Thread;
            public uint ProcessId, ThreadId;
        }

        // 创建 Windows 进程组并返回供启动器持有的句柄
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true, ExactSpelling = true)]
        private static extern SafeFileHandle CreateJobObjectW(IntPtr security, string name);

        // 把扩展限制写入已有进程组
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool SetInformationJobObject(SafeFileHandle handle, int infoClass,
                                                           ref ExtendedLimits info, int size);

        // 打开 Windows 文件或设备，返回按指定继承规则创建的句柄
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true, ExactSpelling = true)]
        private static extern SafeFileHandle CreateFileW(string name, uint access, uint share,
                                                         ref SecurityAttributes security, uint creation,
                                                         uint flags, IntPtr template);

        // 查询启动属性占用的内存，或初始化调用方分配的内存
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool InitializeProcThreadAttributeList(IntPtr list, int count, int flags, ref IntPtr size);

        // 把一项启动属性及其数据写入 Windows 属性列表
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool UpdateProcThreadAttribute(IntPtr list, uint flags, IntPtr attribute,
                                                            IntPtr value, IntPtr size, IntPtr previous, IntPtr returnedSize);

        // 清理属性列表内部状态，外部内存仍由调用方释放
        [DllImport("kernel32.dll")]
        private static extern void DeleteProcThreadAttributeList(IntPtr list);

        // 按指定命令、工作目录和属性创建子进程并返回句柄
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true, ExactSpelling = true)]
        private static extern bool CreateProcessW(string executable, StringBuilder commandLine, IntPtr processSecurity,
                                                  IntPtr threadSecurity, bool inheritHandles, uint flags, IntPtr environment,
                                                  string directory, ref StartupInfoEx startup, out ProcessInfo process);

        // 关闭调用方不再使用的 Windows 句柄
        [DllImport("kernel32.dll")]
        private static extern bool CloseHandle(IntPtr handle);
    }
}
