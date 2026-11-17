using System;
using System.IO;
using System.Linq;
using System.Text;
using System.Web.Script.Serialization;
using System.Windows.Forms;
using WiFiVision.Windows.Models;
using WiFiVision.Windows.Native;
using WiFiVision.Windows.Queue;
using WiFiVision.Windows.Server;
using WiFiVision.Windows.UI;

namespace WiFiVision.Windows
{
    internal static class Program
    {
        [STAThread]
        private static int Main(string[] args)
        {
            int port = LocalCompanionServer.DefaultPort;
            bool scanSmokeOnly = false;
            string smokeOutputFile = null;

            foreach (string arg in args ?? new string[0])
            {
                if (arg.StartsWith("--port=", StringComparison.OrdinalIgnoreCase))
                {
                    int parsed;
                    if (int.TryParse(arg.Substring("--port=".Length), out parsed) && parsed >= 1024 && parsed <= 65535)
                    {
                        port = parsed;
                    }
                }
                else if (arg.Equals("--scan-smoke", StringComparison.OrdinalIgnoreCase))
                {
                    scanSmokeOnly = true;
                }
                else if (arg.StartsWith("--out=", StringComparison.OrdinalIgnoreCase))
                {
                    smokeOutputFile = arg.Substring("--out=".Length);
                }
            }

            if (scanSmokeOnly)
            {
                var scanner = new NativeWlanScanner();
                WindowsScanOutcome outcome = scanner.ScanNow(true);
                var json = new JavaScriptSerializer().Serialize(new
                {
                    success = outcome.Success,
                    errorKind = outcome.ErrorKind.ToString(),
                    statusMessage = outcome.StatusMessage,
                    locationMessage = outcome.LocationMessage,
                    adapter = outcome.Adapter,
                    networkCount = outcome.Networks.Count,
                    sampleNetworks = outcome.Networks.Take(8).Select(n => new
                    {
                        ssid = n.DisplaySsid,
                        bssid = n.Bssid,
                        signalDbm = n.SignalDbm,
                        frequencyMhz = n.FrequencyMhz,
                        channel = n.Channel,
                        band = n.Band,
                        security = n.Security
                    }).ToList()
                });
                if (!string.IsNullOrWhiteSpace(smokeOutputFile))
                {
                    File.WriteAllText(smokeOutputFile, json, Encoding.UTF8);
                }
                Console.WriteLine(json);
                return outcome.Success ? 0 : 1;
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            using (var controller = new WindowsSessionController())
            using (var server = new LocalCompanionServer(controller, port))
            {
                try
                {
                    server.Start();
                }
                catch (Exception ex)
                {
                    MessageBox.Show(
                        "Failed to start local companion API on http://127.0.0.1:" + port + "\n\n" + ex.Message,
                        "WiFiVision Companion Startup Error",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Error);
                    return 2;
                }

                using (var form = new CompanionForm(controller, server))
                {
                    Application.Run(form);
                }
            }

            return 0;
        }
    }
}
