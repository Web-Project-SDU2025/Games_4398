using System;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using Microsoft.Win32.SafeHandles;

namespace Games4398
{
    // The launcher owns the only job handle. Windows closes it even on console termination.
    public sealed class StartupProcessJob : IDisposable
    {
        private const int ExtendedLimitInformation = 9;
        private const uint KillOnJobClose = 0x2000;
        private const uint UseStdHandles = 0x100;
        private const uint CreateNoWindow = 0x08000000;
        private const uint ExtendedStartupInfoPresent = 0x00080000;
        private static readonly IntPtr HandleListAttribute = new IntPtr(0x00020002);
        private static readonly IntPtr JobListAttribute = new IntPtr(0x0002000D);
        private readonly SafeFileHandle job;

        public StartupProcessJob()
        {
            job = CreateJobObjectW(IntPtr.Zero, null);
            if (job.IsInvalid)
                throw new Win32Exception();

            var limits = new ExtendedLimits();
            limits.Basic.LimitFlags = KillOnJobClose;
            if (!SetInformationJobObject(job, ExtendedLimitInformation, ref limits, Marshal.SizeOf(limits)))
            {
                var error = new Win32Exception();
                job.Dispose();
                throw error;
            }
        }

        public WaitHandle Start(string executable, string arguments, string workingDirectory,
                                string stdoutPath, string stderrPath)
        {
            using (var stdin = OpenNullInput())
            using (var stdout = OpenLog(stdoutPath))
            using (var stderr = OpenLog(stderrPath))
            {
                var handles = new[] { stdin.DangerousGetHandle(), stdout.SafeFileHandle.DangerousGetHandle(),
                                      stderr.SafeFileHandle.DangerousGetHandle(), job.DangerousGetHandle() };
                IntPtr size = IntPtr.Zero;
                InitializeProcThreadAttributeList(IntPtr.Zero, 2, 0, ref size);
                if (size == IntPtr.Zero)
                    throw new Win32Exception();

                var attributes = Marshal.AllocHGlobal(size);
                var values = IntPtr.Zero;
                bool initialized = false;
                try
                {
                    values = Marshal.AllocHGlobal(handles.Length * IntPtr.Size);
                    Marshal.Copy(handles, 0, values, handles.Length);
                    initialized = InitializeProcThreadAttributeList(attributes, 2, 0, ref size);
                    // Assign at creation so neither Maven nor Vite can spawn outside this job.
                    if (!initialized ||
                        !UpdateProcThreadAttribute(attributes, 0, HandleListAttribute, values,
                                                   new IntPtr(3 * IntPtr.Size), IntPtr.Zero, IntPtr.Zero) ||
                        !UpdateProcThreadAttribute(attributes, 0, JobListAttribute, IntPtr.Add(values, 3 * IntPtr.Size),
                                                   new IntPtr(IntPtr.Size), IntPtr.Zero, IntPtr.Zero))
                        throw new Win32Exception();

                    var startup = new StartupInfoEx { Attributes = attributes };
                    startup.Info.Size = Marshal.SizeOf(startup);
                    startup.Info.Flags = UseStdHandles;
                    startup.Info.StdInput = handles[0];
                    startup.Info.StdOutput = handles[1];
                    startup.Info.StdError = handles[2];
                    var commandLine = new StringBuilder("\"" + executable + "\" " + arguments);
                    ProcessInfo process;
                    if (!CreateProcessW(executable, commandLine, IntPtr.Zero, IntPtr.Zero, true,
                                        CreateNoWindow | ExtendedStartupInfoPresent, IntPtr.Zero,
                                        workingDirectory, ref startup, out process))
                        throw new Win32Exception();

                    CloseHandle(process.Thread);
                    return new ProcessExitHandle(process.Process);
                }
                finally
                {
                    if (initialized)
                        DeleteProcThreadAttributeList(attributes);
                    Marshal.FreeHGlobal(values);
                    Marshal.FreeHGlobal(attributes);
                }
            }
        }

        public void Dispose()
        {
            job.Dispose();
        }

        private static SafeFileHandle OpenNullInput()
        {
            var security = new SecurityAttributes
            {
                Size = Marshal.SizeOf(typeof(SecurityAttributes)),
                InheritHandle = true
            };
            var input = CreateFileW("NUL", 0x80000000, 3, ref security, 3, 0, IntPtr.Zero);
            if (input.IsInvalid)
                throw new Win32Exception();
            return input;
        }

        private static FileStream OpenLog(string path)
        {
            return new FileStream(path, FileMode.Create, FileAccess.Write, FileShare.ReadWrite | FileShare.Inheritable);
        }

        private sealed class ProcessExitHandle : WaitHandle
        {
            public ProcessExitHandle(IntPtr handle)
            {
                SafeWaitHandle = new SafeWaitHandle(handle, true);
            }
        }

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

        [StructLayout(LayoutKind.Sequential)]
        private struct ExtendedLimits
        {
            public BasicLimits Basic;
            public ulong ReadOperations, WriteOperations, OtherOperations;
            public ulong ReadBytes, WriteBytes, OtherBytes;
            public UIntPtr ProcessMemory, JobMemory, PeakProcessMemory, PeakJobMemory;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct SecurityAttributes
        {
            public int Size;
            public IntPtr SecurityDescriptor;
            [MarshalAs(UnmanagedType.Bool)]
            public bool InheritHandle;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct StartupInfo
        {
            public int Size;
            public IntPtr Reserved, Desktop, Title;
            public uint X, Y, Width, Height, Columns, Rows, FillAttribute, Flags;
            public ushort ShowWindow, ReservedSize;
            public IntPtr ReservedBytes, StdInput, StdOutput, StdError;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct StartupInfoEx
        {
            public StartupInfo Info;
            public IntPtr Attributes;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct ProcessInfo
        {
            public IntPtr Process, Thread;
            public uint ProcessId, ThreadId;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true, ExactSpelling = true)]
        private static extern SafeFileHandle CreateJobObjectW(IntPtr security, string name);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool SetInformationJobObject(SafeFileHandle handle, int infoClass,
                                                           ref ExtendedLimits info, int size);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true, ExactSpelling = true)]
        private static extern SafeFileHandle CreateFileW(string name, uint access, uint share,
                                                         ref SecurityAttributes security, uint creation,
                                                         uint flags, IntPtr template);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool InitializeProcThreadAttributeList(IntPtr list, int count, int flags, ref IntPtr size);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool UpdateProcThreadAttribute(IntPtr list, uint flags, IntPtr attribute,
                                                            IntPtr value, IntPtr size, IntPtr previous, IntPtr returnedSize);

        [DllImport("kernel32.dll")]
        private static extern void DeleteProcThreadAttributeList(IntPtr list);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true, ExactSpelling = true)]
        private static extern bool CreateProcessW(string executable, StringBuilder commandLine, IntPtr processSecurity,
                                                  IntPtr threadSecurity, bool inheritHandles, uint flags, IntPtr environment,
                                                  string directory, ref StartupInfoEx startup, out ProcessInfo process);

        [DllImport("kernel32.dll")]
        private static extern bool CloseHandle(IntPtr handle);
    }
}
