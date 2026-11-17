using System;
using System.Device.Location;
using System.Threading;
using WiFiVision.Windows.Models;

namespace WiFiVision.Windows.Location
{
    public sealed class LocationAcquisitionResult
    {
        public ObservationLocation Location { get; set; }
        public string StatusMessage { get; set; }
    }

    public interface IWindowsLocationProvider
    {
        LocationAcquisitionResult TryGetLocation(int timeoutMs = 2000);
    }

    /// <summary>
    /// Reads Windows Location Services only when supported and permitted.
    /// Never fabricates coordinates or substitutes AP/router locations.
    /// </summary>
    public sealed class WindowsLocationService : IWindowsLocationProvider
    {
        public LocationAcquisitionResult TryGetLocation(int timeoutMs = 2000)
        {
            try
            {
                using (var watcher = new GeoCoordinateWatcher(GeoPositionAccuracy.High))
                {
                    var readyEvent = new ManualResetEventSlim(false);
                    watcher.StatusChanged += (_, e) =>
                    {
                        if (e.Status == GeoPositionStatus.Ready || e.Status == GeoPositionStatus.Disabled)
                            readyEvent.Set();
                    };

                    watcher.Start(true);

                    if (watcher.Permission == GeoPositionPermission.Denied ||
                        watcher.Status == GeoPositionStatus.Disabled)
                    {
                        watcher.Stop();
                        return new LocationAcquisitionResult
                        {
                            Location = null,
                            StatusMessage = "Windows location disabled or permission denied (uploading without coordinates)."
                        };
                    }

                    readyEvent.Wait(timeoutMs);
                    var pos = watcher.Position;
                    watcher.Stop();

                    if (pos == null || pos.Location == null || pos.Location.IsUnknown)
                    {
                        return new LocationAcquisitionResult
                        {
                            Location = null,
                            StatusMessage = "Location unavailable on this PC (observations saved without coordinates)."
                        };
                    }

                    double? accuracy = !double.IsNaN(pos.Location.HorizontalAccuracy) && pos.Location.HorizontalAccuracy >= 0
                        ? (double?)Math.Round(pos.Location.HorizontalAccuracy, 1)
                        : null;

                    var loc = new ObservationLocation
                    {
                        Latitude = pos.Location.Latitude,
                        Longitude = pos.Location.Longitude,
                        AccuracyMeters = accuracy
                    };

                    if (!loc.IsValid())
                    {
                        return new LocationAcquisitionResult
                        {
                            Location = null,
                            StatusMessage = "Invalid Windows location reading ignored."
                        };
                    }

                    return new LocationAcquisitionResult
                    {
                        Location = loc,
                        StatusMessage = accuracy.HasValue
                            ? string.Format("Windows location active (±{0:F1} m)", accuracy.Value)
                            : "Windows location active"
                    };
                }
            }
            catch
            {
                return new LocationAcquisitionResult
                {
                    Location = null,
                    StatusMessage = "Windows location service unavailable (observations saved without coordinates)."
                };
            }
        }
    }
}
