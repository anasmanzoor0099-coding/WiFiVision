using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Threading.Tasks;
using System.Windows.Forms;
using WiFiVision.Windows.Models;
using WiFiVision.Windows.Network;
using WiFiVision.Windows.Queue;
using WiFiVision.Windows.Server;

namespace WiFiVision.Windows.UI
{
    public sealed class CompanionForm : Form
    {
        private static readonly Color WarmIvory = ColorTranslator.FromHtml("#F7F6F2");
        private static readonly Color PureWhite = ColorTranslator.FromHtml("#FFFFFF");
        private static readonly Color DarkSlate = ColorTranslator.FromHtml("#303640");
        private static readonly Color ChampagneGold = ColorTranslator.FromHtml("#C6A15B");
        private static readonly Color SoftBorder = ColorTranslator.FromHtml("#E4E1D8");
        private static readonly Color MutedInk = ColorTranslator.FromHtml("#646B76");

        private readonly WindowsSessionController _controller;
        private readonly LocalCompanionServer _localServer;
        private readonly BackendApiClient _apiClient = new BackendApiClient();

        private Label _statusBannerLabel;
        private Label _pairingBadgeLabel;
        private Label _localApiBadgeLabel;
        private Label _adapterLabel;
        private Label _locationLabel;
        private Label _scanCountLabel;
        private Label _queuedCountLabel;
        private Label _uploadedCountLabel;
        private TextBox _backendUrlBox;
        private TextBox _pairingCodeBox;
        private TextBox _deviceNameBox;
        private TextBox _localTokenBox;
        private Button _toggleTokenBtn;
        private Button _copyTokenBtn;
        private Button _checkHealthBtn;
        private Button _pairBtn;
        private Button _clearPairingBtn;
        private Button _scanBtn;
        private Button _monitorBtn;
        private Button _uploadBtn;
        private DataGridView _networksGrid;
        private bool _tokenRevealed;

        public CompanionForm(WindowsSessionController controller, LocalCompanionServer localServer)
        {
            if (controller == null) throw new ArgumentNullException("controller");
            if (localServer == null) throw new ArgumentNullException("localServer");
            _controller = controller;
            _localServer = localServer;

            Text = "WiFiVision — Windows Companion Scanner";
            Size = new Size(1060, 720);
            MinimumSize = new Size(920, 620);
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = WarmIvory;
            ForeColor = DarkSlate;
            Font = new Font("Segoe UI", 9.5f);

            BuildLayout();
            _controller.StateChanged += OnControllerStateChanged;
            RefreshUiFromSnapshot(_controller.GetSnapshot());
        }

        private void BuildLayout()
        {
            var root = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 1,
                RowCount = 3,
                Padding = new Padding(16),
                BackColor = WarmIvory
            };
            root.RowStyles.Add(new RowStyle(SizeType.Absolute, 86));
            root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            root.RowStyles.Add(new RowStyle(SizeType.Absolute, 36));
            Controls.Add(root);

            // 1. Header Card
            var headerCard = CreateCardPanel();
            headerCard.Dock = DockStyle.Fill;
            headerCard.Padding = new Padding(16, 12, 16, 12);

            var title = new Label
            {
                Text = "WiFiVision Windows Companion",
                Font = new Font("Segoe UI Semibold", 15f),
                ForeColor = DarkSlate,
                AutoSize = true,
                Location = new Point(16, 12)
            };
            var subtitle = new Label
            {
                Text = "Native 802.11 WLAN scanner • Privacy-first manual upload • Loopback bridge on http://127.0.0.1:" + _localServer.Port,
                Font = new Font("Segoe UI", 9f),
                ForeColor = MutedInk,
                AutoSize = true,
                Location = new Point(18, 42)
            };
            _pairingBadgeLabel = new Label
            {
                Text = "NOT PAIRED",
                Font = new Font("Segoe UI Semibold", 8.5f),
                AutoSize = true,
                Padding = new Padding(10, 5, 10, 5),
                BackColor = WarmIvory,
                ForeColor = DarkSlate,
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                Location = new Point(740, 16)
            };
            _localApiBadgeLabel = new Label
            {
                Text = "LOCAL API :" + _localServer.Port,
                Font = new Font("Segoe UI Semibold", 8.5f),
                AutoSize = true,
                Padding = new Padding(10, 5, 10, 5),
                BackColor = ChampagneGold,
                ForeColor = DarkSlate,
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                Location = new Point(850, 16)
            };

            headerCard.Controls.AddRange(new Control[] { title, subtitle, _pairingBadgeLabel, _localApiBadgeLabel });
            root.Controls.Add(headerCard, 0, 0);

            // 2. Main Split Body
            var body = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 2,
                RowCount = 1,
                Margin = new Padding(0, 12, 0, 8)
            };
            body.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 360));
            body.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            root.Controls.Add(body, 0, 1);

            // Left Column: Pairing & Local Bridge Token
            var leftCard = CreateCardPanel();
            leftCard.Dock = DockStyle.Fill;
            leftCard.Padding = new Padding(14);
            body.Controls.Add(leftCard, 0, 0);

            int y = 14;
            leftCard.Controls.Add(MakeSectionHeading("DEVICE PAIRING & LOCAL BRIDGE", 14, y));
            y += 28;

            leftCard.Controls.Add(MakeFieldLabel("Backend API URL", 14, y));
            y += 20;
            _backendUrlBox = new TextBox { Location = new Point(14, y), Width = 316, Text = "http://localhost:5000" };
            leftCard.Controls.Add(_backendUrlBox);
            y += 30;

            _checkHealthBtn = MakeSecondaryButton("Verify Backend Connection", 14, y, 316);
            _checkHealthBtn.Click += OnCheckHealthClick;
            leftCard.Controls.Add(_checkHealthBtn);
            y += 40;

            leftCard.Controls.Add(MakeFieldLabel("One-Time Pairing Code (from Web Settings)", 14, y));
            y += 20;
            _pairingCodeBox = new TextBox { Location = new Point(14, y), Width = 316, MaxLength = 12 };
            leftCard.Controls.Add(_pairingCodeBox);
            y += 30;

            leftCard.Controls.Add(MakeFieldLabel("Windows Device Name", 14, y));
            y += 20;
            _deviceNameBox = new TextBox { Location = new Point(14, y), Width = 316, MaxLength = 60, Text = Environment.MachineName };
            leftCard.Controls.Add(_deviceNameBox);
            y += 32;

            _pairBtn = MakePrimaryButton("Pair This Windows PC", 14, y, 200);
            _pairBtn.Click += OnPairClick;
            _clearPairingBtn = MakeSecondaryButton("Clear Local", 222, y, 108);
            _clearPairingBtn.Click += OnClearPairingClick;
            leftCard.Controls.Add(_pairBtn);
            leftCard.Controls.Add(_clearPairingBtn);
            y += 46;

            leftCard.Controls.Add(MakeSectionHeading("BROWSER LOCAL API SESSION TOKEN", 14, y));
            y += 24;
            leftCard.Controls.Add(MakeFieldLabel("Paste into Web Settings / Monitoring to link browser:", 14, y));
            y += 20;
            _localTokenBox = new TextBox
            {
                Location = new Point(14, y),
                Width = 316,
                ReadOnly = true,
                UseSystemPasswordChar = true,
                Text = _localServer.LocalApiToken
            };
            leftCard.Controls.Add(_localTokenBox);
            y += 28;

            _toggleTokenBtn = MakeSecondaryButton("Reveal Token", 14, y, 152);
            _toggleTokenBtn.Click += (s, e) =>
            {
                _tokenRevealed = !_tokenRevealed;
                _localTokenBox.UseSystemPasswordChar = !_tokenRevealed;
                _toggleTokenBtn.Text = _tokenRevealed ? "Hide Token" : "Reveal Token";
            };
            _copyTokenBtn = MakeSecondaryButton("Copy Local Token", 174, y, 156);
            _copyTokenBtn.Click += (s, e) =>
            {
                Clipboard.SetText(_localServer.LocalApiToken);
                _statusBannerLabel.Text = "Copied X-WiFiVision-Local-Token to clipboard.";
            };
            leftCard.Controls.Add(_toggleTokenBtn);
            leftCard.Controls.Add(_copyTokenBtn);
            y += 44;

            leftCard.Controls.Add(MakeSectionHeading("HARDWARE & LOCATION STATUS", 14, y));
            y += 24;
            _adapterLabel = new Label { Location = new Point(14, y), Size = new Size(316, 44), ForeColor = DarkSlate };
            leftCard.Controls.Add(_adapterLabel);
            y += 46;
            _locationLabel = new Label { Location = new Point(14, y), Size = new Size(316, 44), ForeColor = MutedInk };
            leftCard.Controls.Add(_locationLabel);

            // Right Column: Metrics, Workflow Buttons, Scan Table
            var rightLayout = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 1,
                RowCount = 3,
                Margin = new Padding(12, 0, 0, 0)
            };
            rightLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 88));
            rightLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 52));
            rightLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            body.Controls.Add(rightLayout, 1, 0);

            // Row 0: Three distinct metric cards
            var metricsGrid = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 3, RowCount = 1 };
            metricsGrid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 33.33f));
            metricsGrid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 33.33f));
            metricsGrid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 33.34f));

            _scanCountLabel = CreateMetricCard(metricsGrid, 0, "LOCAL SCAN RESULTS", "0");
            _queuedCountLabel = CreateMetricCard(metricsGrid, 1, "QUEUED (UNSENT)", "0");
            _uploadedCountLabel = CreateMetricCard(metricsGrid, 2, "UPLOADED COUNT", "0");
            rightLayout.Controls.Add(metricsGrid, 0, 0);

            // Row 1: Action Toolbar
            var toolbar = new FlowLayoutPanel
            {
                Dock = DockStyle.Fill,
                FlowDirection = FlowDirection.LeftToRight,
                Padding = new Padding(0, 8, 0, 0)
            };
            _scanBtn = MakePrimaryButton("SCAN ONLY", 0, 0, 145);
            _scanBtn.Click += OnScanOnlyClick;
            _monitorBtn = MakeSecondaryButton("START MONITOR", 0, 0, 165);
            _monitorBtn.Click += OnToggleMonitorClick;
            _uploadBtn = MakeSecondaryButton("UPLOAD OBSERVATIONS", 0, 0, 210);
            _uploadBtn.Click += OnUploadClick;
            toolbar.Controls.AddRange(new Control[] { _scanBtn, _monitorBtn, _uploadBtn });
            rightLayout.Controls.Add(toolbar, 0, 1);

            // Row 2: Scanned Networks Table Card
            var tableCard = CreateCardPanel();
            tableCard.Dock = DockStyle.Fill;
            tableCard.Padding = new Padding(8);
            _networksGrid = new DataGridView
            {
                Dock = DockStyle.Fill,
                ReadOnly = true,
                AllowUserToAddRows = false,
                AllowUserToDeleteRows = false,
                RowHeadersVisible = false,
                SelectionMode = DataGridViewSelectionMode.FullRowSelect,
                AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill,
                BackgroundColor = PureWhite,
                BorderStyle = BorderStyle.None
            };
            _networksGrid.Columns.Add("Ssid", "SSID");
            _networksGrid.Columns.Add("Bssid", "BSSID");
            _networksGrid.Columns.Add("Signal", "Signal (dBm)");
            _networksGrid.Columns.Add("Band", "Band");
            _networksGrid.Columns.Add("Channel", "Ch");
            _networksGrid.Columns.Add("Security", "Security");
            tableCard.Controls.Add(_networksGrid);
            rightLayout.Controls.Add(tableCard, 0, 2);

            // 3. Status Footer
            _statusBannerLabel = new Label
            {
                Dock = DockStyle.Fill,
                TextAlign = ContentAlignment.MiddleLeft,
                ForeColor = DarkSlate,
                Font = new Font("Segoe UI", 9f),
                Text = "Ready."
            };
            root.Controls.Add(_statusBannerLabel, 0, 2);
        }

        private async void OnCheckHealthClick(object sender, EventArgs e)
        {
            string url = _backendUrlBox.Text;
            _controller.SaveBackendBaseUrl(url);
            _checkHealthBtn.Enabled = false;
            HealthStatusResult res = await Task.Run(() => _apiClient.CheckHealth(url));
            _checkHealthBtn.Enabled = true;
            _statusBannerLabel.Text = res.Healthy ? "Backend OK: " + res.Message : "Backend Check: " + res.Message;
        }

        private async void OnPairClick(object sender, EventArgs e)
        {
            string url = _backendUrlBox.Text;
            string code = _pairingCodeBox.Text;
            string name = _deviceNameBox.Text;
            _pairBtn.Enabled = false;
            PairingResult res = await Task.Run(() => _controller.PairDevice(url, code, name));
            _pairBtn.Enabled = true;
            if (res.Success)
            {
                _pairingCodeBox.Text = "";
                MessageBox.Show(this, "Windows PC paired securely as \"" + res.Credential.DeviceName + "\" using DPAPI storage.", "Paired", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
            else
            {
                MessageBox.Show(this, res.Message ?? "Pairing failed.", "Pairing Error", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        private void OnClearPairingClick(object sender, EventArgs e)
        {
            _controller.ClearPairing();
        }

        private async void OnScanOnlyClick(object sender, EventArgs e)
        {
            _scanBtn.Enabled = false;
            await Task.Run(() => _controller.ScanOnly(true));
            _scanBtn.Enabled = true;
        }

        private void OnToggleMonitorClick(object sender, EventArgs e)
        {
            var snap = _controller.GetSnapshot();
            if (snap.MonitorActive) _controller.StopMonitoring();
            else _controller.StartMonitoring();
        }

        private async void OnUploadClick(object sender, EventArgs e)
        {
            UploadConfirmationPreview preview = _controller.BuildUploadPreview();
            int total = preview.ExistingPendingQueueCount + preview.ReadyLocalCount;
            if (total == 0)
            {
                MessageBox.Show(this, "No scanned or queued observations to upload yet. Click SCAN ONLY first.", "Nothing Queued", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            string locLine = preview.HasLocation
                ? (preview.LocationAccuracyMeters.HasValue ? "Windows Location attached (±" + Math.Round(preview.LocationAccuracyMeters.Value) + " m)" : "Windows Location attached")
                : "Windows Location unavailable (uploads with null location — never guessed or mocked)";

            string details =
                "Explicit Confirmation Required (Auto-upload is OFF)\n\n" +
                "Queued Pending: " + preview.ExistingPendingQueueCount + "\n" +
                "Latest Scan Ready: " + preview.ReadyLocalCount + "\n" +
                "Total to Upload: " + total + "\n" +
                "Location: " + locLine + "\n\n" +
                string.Join("\n", preview.PreviewLines) +
                "\n\nClick YES to trigger UPLOAD NOW, or NO to keep observations local.";

            DialogResult choice = MessageBox.Show(this, details, "Confirm UPLOAD NOW (" + total + ")", MessageBoxButtons.YesNo, MessageBoxIcon.Question);
            if (choice != DialogResult.Yes) return;

            _uploadBtn.Enabled = false;
            UploadExecutionResult result = await Task.Run(() => _controller.ConfirmUploadNow(total));
            _uploadBtn.Enabled = true;
            MessageBox.Show(this, result.Message, "Upload Summary", MessageBoxButtons.OK, result.RequiresReparing ? MessageBoxIcon.Warning : MessageBoxIcon.Information);
        }

        private void OnControllerStateChanged()
        {
            if (IsDisposed || !IsHandleCreated) return;
            BeginInvoke(new Action(() => RefreshUiFromSnapshot(_controller.GetSnapshot())));
        }

        private void RefreshUiFromSnapshot(CompanionStateSnapshot snap)
        {
            if (snap == null) return;
            if (!_backendUrlBox.Focused && !string.IsNullOrWhiteSpace(snap.BackendBaseUrl))
            {
                _backendUrlBox.Text = snap.BackendBaseUrl;
            }

            _pairingBadgeLabel.Text = snap.Paired ? "PAIRED (" + (snap.DeviceName ?? "Windows") + ")" : "NOT PAIRED";
            _pairingBadgeLabel.BackColor = snap.Paired ? ColorTranslator.FromHtml("#E7F3EC") : WarmIvory;
            _clearPairingBtn.Enabled = snap.Paired;
            _uploadBtn.Enabled = !snap.UploadInProgress;
            _monitorBtn.Text = snap.MonitorActive ? "STOP MONITOR" : "START MONITOR";

            _adapterLabel.Text = snap.Adapter != null
                ? "Adapter: " + snap.Adapter.Description + "\nState: " + snap.Adapter.State
                : "Adapter: Checking WLAN status...";
            _locationLabel.Text = snap.LastScan != null
                ? snap.LastScan.LocationMessage
                : "Location: Requested only during user-initiated scans.";

            int scanCount = snap.LastScan != null ? snap.LastScan.Networks.Count : 0;
            _scanCountLabel.Text = scanCount.ToString();
            _queuedCountLabel.Text = snap.QueuedCount.ToString();
            _uploadedCountLabel.Text = snap.UploadedCount.ToString();
            _statusBannerLabel.Text = snap.StatusBanner ?? "Ready.";

            _networksGrid.Rows.Clear();
            if (snap.LastScan != null && snap.LastScan.Networks != null)
            {
                foreach (WindowsScanNetwork net in snap.LastScan.Networks)
                {
                    _networksGrid.Rows.Add(
                        net.DisplaySsid,
                        net.Bssid ?? "N/A",
                        net.SignalDbm + " dBm",
                        net.Band,
                        net.Channel.HasValue ? net.Channel.Value.ToString() : "—",
                        net.Security);
                }
            }
        }

        private static Panel CreateCardPanel()
        {
            return new Panel
            {
                BackColor = PureWhite,
                BorderStyle = BorderStyle.FixedSingle
            };
        }

        private static Label CreateMetricCard(TableLayoutPanel parent, int col, string title, string value)
        {
            var card = new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = PureWhite,
                BorderStyle = BorderStyle.FixedSingle,
                Margin = new Padding(col == 0 ? 0 : 6, 0, col == 2 ? 0 : 6, 0),
                Padding = new Padding(12, 10, 12, 8)
            };
            var lblTitle = new Label
            {
                Text = title,
                Font = new Font("Segoe UI Semibold", 8f),
                ForeColor = MutedInk,
                AutoSize = true,
                Location = new Point(12, 10)
            };
            var lblValue = new Label
            {
                Text = value,
                Font = new Font("Segoe UI Semibold", 20f),
                ForeColor = DarkSlate,
                AutoSize = true,
                Location = new Point(12, 32)
            };
            card.Controls.Add(lblTitle);
            card.Controls.Add(lblValue);
            parent.Controls.Add(card, col, 0);
            return lblValue;
        }

        private static Label MakeSectionHeading(string text, int x, int y)
        {
            return new Label
            {
                Text = text,
                Font = new Font("Segoe UI Semibold", 8.5f),
                ForeColor = ChampagneGold,
                AutoSize = true,
                Location = new Point(x, y)
            };
        }

        private static Label MakeFieldLabel(string text, int x, int y)
        {
            return new Label
            {
                Text = text,
                Font = new Font("Segoe UI", 8.5f),
                ForeColor = MutedInk,
                AutoSize = true,
                Location = new Point(x, y)
            };
        }

        private static Button MakePrimaryButton(string text, int x, int y, int width)
        {
            return new Button
            {
                Text = text,
                Location = new Point(x, y),
                Size = new Size(width, 34),
                FlatStyle = FlatStyle.Flat,
                BackColor = DarkSlate,
                ForeColor = PureWhite,
                Font = new Font("Segoe UI Semibold", 9f),
                Cursor = Cursors.Hand
            };
        }

        private static Button MakeSecondaryButton(string text, int x, int y, int width)
        {
            var btn = new Button
            {
                Text = text,
                Location = new Point(x, y),
                Size = new Size(width, 34),
                FlatStyle = FlatStyle.Flat,
                BackColor = WarmIvory,
                ForeColor = DarkSlate,
                Font = new Font("Segoe UI Semibold", 9f),
                Cursor = Cursors.Hand
            };
            btn.FlatAppearance.BorderColor = SoftBorder;
            return btn;
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                _controller.StateChanged -= OnControllerStateChanged;
            }
            base.Dispose(disposing);
        }
    }
}
