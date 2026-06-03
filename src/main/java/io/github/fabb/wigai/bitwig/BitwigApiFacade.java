package io.github.fabb.wigai.bitwig;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.api.*;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.data.ParameterInfo;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.error.WigAIErrorHandler;
import io.github.fabb.wigai.common.validation.ParameterValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Facade for Bitwig API interactions.
 * This class abstracts the Bitwig API and provides simplified methods for common operations.
 */
public class BitwigApiFacade {

    /**
     * Constants used throughout the BitwigApiFacade.
     */
    private static final class Constants {
        public static final String DEFAULT_PROJECT_NAME = "Unknown Project";
        public static final String DEFAULT_BEAT_POSITION = "1.1.1:0";
        public static final String DEFAULT_COLOR = "rgb(128,128,128)";
        public static final String DEFAULT_TIME_STRING = "0:00.000";
        public static final int MAX_TRACKS = 128;
        public static final int MAX_SCENES = 128;
        public static final int MAX_DEVICES_PER_TRACK = 128;
        public static final int TICKS_PER_SIXTEENTH = 240;
        public static final int BEATS_PER_MEASURE = 4;
        public static final int SIXTEENTHS_PER_BEAT = 4;
        public static final int DEVICE_PARAMETER_COUNT = 8;
        public static final int PROJECT_PARAMETER_COUNT = 8;

        private Constants() {} // Prevent instantiation
    }

    private final ControllerHost host;
    private final Transport transport;
    private final Application application;
    private final Logger logger;
    private final CursorDevice cursorDevice;
    private final RemoteControlsPage deviceParameterBank;
    private final TrackBank trackBank;
    private final SceneBankFacade sceneBankFacade;
    private final CursorTrack cursorTrack;
    private final RemoteControlsPage projectParameterBank;
    private final List<DeviceBank> trackDeviceBanks;
    private final Clip cursorClip;

    // Preset/Page caching — updated by observers in constructor
    private volatile String[] presetNames = new String[0];
    private volatile String[] presetCategories = new String[0];
    private volatile String[] presetCreators = new String[0];
    private volatile String[] pageNames = new String[0];
    private volatile int selectedPresetIndex = -1;
    private volatile int selectedPageIndex = -1;

    // Bitwig built-in device UUID lookup table (extracted from Bitwig 6.0.6)
    private static final java.util.Map<String, String> DEVICE_UUID_MAP = new java.util.LinkedHashMap<>();
    static {
        // Synths
        DEVICE_UUID_MAP.put("polysynth", "a9ffacb5-33e9-4fc7-8621-b1af31e410ef");
        DEVICE_UUID_MAP.put("phase-4", "252723bf-68a6-4ee6-81f8-95ba4d0fb467");
        DEVICE_UUID_MAP.put("polymer", "8f58138b-03aa-4e9d-83bd-a038c99a4ed5");
        DEVICE_UUID_MAP.put("fm-4", "7a0a94df-3aa4-4bb5-8e24-2511999871ad");
        DEVICE_UUID_MAP.put("sampler", "468bc14b-b2e7-45a1-9666-e83117fe404e");
        DEVICE_UUID_MAP.put("organ", "f2dcfe9a-7b66-4c84-984a-b25685a1c21a");
        // Drum
        DEVICE_UUID_MAP.put("drum machine", "8ea97e45-0255-40fd-bc7e-94419741e9d1");
        // Grid
        DEVICE_UUID_MAP.put("poly grid", "a33bba66-8cd4-4f89-aee5-68bf67f70a54");
        DEVICE_UUID_MAP.put("fx grid", "d641f61b-d4db-4006-930e-cdd7aeb3e9d7");
        DEVICE_UUID_MAP.put("note grid", "264d6f4e-5067-46c9-a4fa-a75a295d9e01");
        // Container
        DEVICE_UUID_MAP.put("instrument layer", "5024be2e-65d6-4d40-bbfe-8b2ea993c445");
        DEVICE_UUID_MAP.put("instrument selector", "9588fbcf-721a-438b-8555-97e4231f7d2c");
        DEVICE_UUID_MAP.put("fx layer", "a0913b7f-096b-4ac9-bddd-33c775314b42");
        DEVICE_UUID_MAP.put("fx selector", "956e396b-07c5-4430-a58d-8dcfc316522a");
        DEVICE_UUID_MAP.put("chain", "c86d21fb-d544-4daf-a1bf-57de22aa320c");
        // EQ
        DEVICE_UUID_MAP.put("eq+", "e4815188-ba6f-4d14-bcfc-2dcb8f778ccb");
        DEVICE_UUID_MAP.put("eq-2", "01af068e-1e49-4777-a6e6-7f1dc679227a");
        DEVICE_UUID_MAP.put("eq-5", "227e2e3c-75d5-46f3-960d-8fb5529fe29f");
        DEVICE_UUID_MAP.put("eq-dj", "3cc1b71a-e22a-42cf-89f0-316475368fb3");
        // Dynamics
        DEVICE_UUID_MAP.put("compressor", "2b1b4787-8d74-4138-877b-9197209eef0f");
        DEVICE_UUID_MAP.put("compressor+", "42b32cd2-6275-4ff1-970f-4fac71d15ad9");
        DEVICE_UUID_MAP.put("gate", "556300ac-3a6e-4423-966a-5d5dde459a1b");
        DEVICE_UUID_MAP.put("peak limiter", "8da7251e-2578-4bcc-b3c4-8f4ec2e115d0");
        DEVICE_UUID_MAP.put("transient control", "71e6dbd8-a117-4ff0-85e8-5650f5a76d98");
        DEVICE_UUID_MAP.put("tool", "e67b9c56-838d-4fba-8e3e-ae4e02cccbcb");
        // Reverb/Delay
        DEVICE_UUID_MAP.put("reverb", "5a1cb339-1c4a-4cc7-9cae-bd7a2058153d");
        DEVICE_UUID_MAP.put("delay+", "f2baa2a8-36c5-4a79-b1d9-a4e461c45ee9");
        DEVICE_UUID_MAP.put("delay-1", "2a7a7328-3f7a-4afb-95eb-5230c298bb90");
        DEVICE_UUID_MAP.put("delay-2", "71539d5d-1c7a-4dac-8f74-29e23b89b599");
        DEVICE_UUID_MAP.put("delay-4", "f95a0e18-5a8b-4f53-93ad-8be73fd668bd");
        // Modulation
        DEVICE_UUID_MAP.put("chorus", "d275f9a6-0e4a-409c-9dc4-d74af90bc7ae");
        DEVICE_UUID_MAP.put("flanger", "8393c436-b11b-4fee-85dd-b2ef0a2ed380");
        DEVICE_UUID_MAP.put("phaser", "fc87ae07-1624-449f-8dae-2db5d93e1aa9");
        DEVICE_UUID_MAP.put("ring-mod", "374feaeb-c785-4243-9d08-3f9099b4c0cb");
        DEVICE_UUID_MAP.put("tremolo", "f3b90fff-402b-4187-9aab-620f441577b9");
        // Filter
        DEVICE_UUID_MAP.put("filter", "4ccfc70e-59bd-4e97-a8a7-d8cdce88bf42");
        DEVICE_UUID_MAP.put("filter+", "6d621c1c-ab64-43b4-aea3-dad37e6f649c");
        DEVICE_UUID_MAP.put("sweep", "ab52804f-1169-4657-b8c8-8db5532cf717");
        DEVICE_UUID_MAP.put("ladder", "abfbbd63-8801-4bdb-a1ad-4b197f4d41e0");
        DEVICE_UUID_MAP.put("comb", "20e18780-8438-48d3-b234-40dcbaa947b8");
        // Distortion
        DEVICE_UUID_MAP.put("distortion", "b5b2b08e-730e-4192-be71-f572ceb5069b");
        DEVICE_UUID_MAP.put("saturator", "93d11348-86ae-4ead-9fe7-84ac03b9369c");
        DEVICE_UUID_MAP.put("bit-8", "43875255-6f1f-4d54-a5ad-c45bff793477");
        DEVICE_UUID_MAP.put("amp", "41be8f3a-6d24-4442-9508-8548dbe62d47");
        DEVICE_UUID_MAP.put("over", "41b34699-8e5d-4534-a429-a67d488ba6ac");
        // Note FX
        DEVICE_UUID_MAP.put("arpeggiator", "4d407a2b-c91b-4e4c-9a89-c53c19fe6251");
        DEVICE_UUID_MAP.put("note repeater", "a68e0f1b-bcc6-45c2-b09e-8c8771f83e50");
        DEVICE_UUID_MAP.put("bend", "6aec6e78-9c1e-4c0b-8a88-0c2c37890a1d");
        DEVICE_UUID_MAP.put("echo", "43c102c9-ce32-4dd8-b207-f0831733b17b");
        DEVICE_UUID_MAP.put("key filter", "f14bacde-084c-4f14-8bdf-d8c4fda8b368");
        DEVICE_UUID_MAP.put("note filter", "ef7559c8-49ae-4657-95be-11abb896c969");
        DEVICE_UUID_MAP.put("note transpose", "0815cd9e-3a31-4429-a268-dabd952a3b68");
        DEVICE_UUID_MAP.put("humanize", "f7b6f2a6-bfca-41ec-8646-b68e0f4cf12b");
        DEVICE_UUID_MAP.put("multi-note", "0a015261-7546-4f6d-9197-098a26ff2c20");
        DEVICE_UUID_MAP.put("quantize", "1c116b76-2b07-4b16-bf2a-ed5f0bdcc661");
        // Audio FX
        DEVICE_UUID_MAP.put("freq shifter", "7ec87fdf-0bf8-42e7-b54b-5d8b68e330b1");
        DEVICE_UUID_MAP.put("blur", "72a3018d-788b-472c-b1d7-16419d00f4c6");
        DEVICE_UUID_MAP.put("pitch shifter", "384fe469-6023-4f69-9560-e0c2eec2da49");
        DEVICE_UUID_MAP.put("treemonster", "e45e00d2-85a0-4c05-8321-819694befa09");
        // Analysis
        DEVICE_UUID_MAP.put("spectrum", "fcd9aa65-ebbb-4337-a97e-69929322ef47");
        DEVICE_UUID_MAP.put("oscilloscope", "ffe670a2-09aa-4c9b-8822-5161a9cca686");
    }

    /**
     * Creates a new BitwigApiFacade instance.
     *
     * @param host   The Bitwig ControllerHost
     * @param logger The logger for logging operations
     */
    public BitwigApiFacade(ControllerHost host, Logger logger) {
        this.host = host;
        this.transport = host.createTransport();
        this.application = host.createApplication();
        this.logger = logger;

        // Mark transport properties as interested for status queries
        transport.isPlaying().markInterested();
        transport.isArrangerRecordEnabled().markInterested();
        transport.isArrangerLoopEnabled().markInterested();
        transport.isMetronomeEnabled().markInterested();
        transport.tempo().markInterested();
        transport.tempo().value().markInterested();
        transport.timeSignature().markInterested();
        transport.getPosition().markInterested();
        transport.playPositionInSeconds().markInterested();

        // Mark application properties as interested for status queries
        application.projectName().markInterested();
        application.hasActiveEngine().markInterested();

        // Initialize device control - use CursorTrack.createCursorDevice() instead of deprecated host.createCursorDevice()
        this.cursorTrack = host.createCursorTrack(0, 0);
        this.cursorDevice = cursorTrack.createCursorDevice();
        this.deviceParameterBank = cursorDevice.createCursorRemoteControlsPage(Constants.DEVICE_PARAMETER_COUNT);

        // Initialize project parameter access via MasterTrack (project parameters)
        MasterTrack masterTrack = host.createMasterTrack(0);
        this.projectParameterBank = masterTrack.createCursorRemoteControlsPage(Constants.PROJECT_PARAMETER_COUNT);

        // Initialize track bank for clip launching (support up to 128 tracks and 128 scenes for full functionality)
        this.trackBank = host.createTrackBank(Constants.MAX_TRACKS, 0, Constants.MAX_SCENES);
        this.sceneBankFacade = new SceneBankFacade(host, logger, Constants.MAX_SCENES); // Support up to 128 scenes for full functionality

        // Initialize cursor clip for note/clip content operations (128 steps x 128 keys)
        this.cursorClip = host.createCursorClip(128, 128);
        cursorClip.exists().markInterested();
        cursorClip.getPlayStart().markInterested();
        cursorClip.getPlayStop().markInterested();

        // Initialize device banks for each track to enable device enumeration
        this.trackDeviceBanks = new ArrayList<>();
        for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
            Track track = trackBank.getItemAt(i);
            DeviceBank deviceBank = track.createDeviceBank(Constants.MAX_DEVICES_PER_TRACK);
            trackDeviceBanks.add(deviceBank);
        }

        // Mark interest in device properties to enable value access
        cursorDevice.exists().markInterested();
        cursorDevice.name().markInterested();
        cursorDevice.isEnabled().markInterested();
        cursorDevice.deviceType().markInterested();

        // Mark interest in all device parameter properties to enable value access
        for (int i = 0; i < deviceParameterBank.getParameterCount(); i++) {
            RemoteControl parameter = deviceParameterBank.getParameter(i);
            parameter.exists().markInterested();
            parameter.name().markInterested();
            parameter.value().markInterested();
            parameter.displayedValue().markInterested();
        }

        // Register preset/Page observers on cursorDevice for autonomous sound selection
        cursorDevice.addPresetNamesObserver(names -> { this.presetNames = names; this.selectedPresetIndex = findPresetIndex(); });
        cursorDevice.addPresetCategoriesObserver(cats -> { this.presetCategories = cats; });
        cursorDevice.addPresetCreatorsObserver(creators -> { this.presetCreators = creators; });
        cursorDevice.addPageNamesObserver(pages -> { this.pageNames = pages; });
        cursorDevice.addSelectedPageObserver(0, pageIdx -> { this.selectedPageIndex = pageIdx; });

        // Mark interest in project parameters to enable value access
        for (int i = 0; i < projectParameterBank.getParameterCount(); i++) {
            RemoteControl parameter = projectParameterBank.getParameter(i);
            parameter.exists().markInterested();
            parameter.name().markInterested();
            parameter.value().markInterested();
            parameter.displayedValue().markInterested();
        }

        // Mark interest in cursor track properties for selected track details
        cursorTrack.exists().markInterested();
        cursorTrack.name().markInterested();
        cursorTrack.trackType().markInterested();
        cursorTrack.isGroup().markInterested();
        cursorTrack.mute().markInterested();
        cursorTrack.solo().markInterested();
        cursorTrack.arm().markInterested();
        cursorTrack.position().markInterested();
        cursorTrack.isMonitoring().markInterested();
        cursorTrack.monitorMode().markInterested();
        cursorTrack.volume().value().markInterested();
        cursorTrack.volume().displayedValue().markInterested();
        cursorTrack.pan().value().markInterested();
        cursorTrack.pan().displayedValue().markInterested();

        // Mark interest in track properties for clip launching and track listing
        for (int trackIndex = 0; trackIndex < trackBank.getSizeOfBank(); trackIndex++) {
            Track track = trackBank.getItemAt(trackIndex);
            track.name().markInterested();
            track.exists().markInterested();
            track.trackType().markInterested();
            track.isGroup().markInterested();
            track.isActivated().markInterested();
            track.color().markInterested();

            // Mark interest in device properties for this track
            DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
            for (int deviceIndex = 0; deviceIndex < deviceBank.getSizeOfBank(); deviceIndex++) {
                Device device = deviceBank.getItemAt(deviceIndex);
                device.exists().markInterested();
                device.name().markInterested();
                device.isEnabled().markInterested();
                device.deviceType().markInterested();
            }

            // Mark interest in commonly used channel controls
            track.mute().markInterested();
            track.solo().markInterested();
            track.arm().markInterested();
            track.volume().value().markInterested();
            track.volume().displayedValue().markInterested();
            track.pan().value().markInterested();
            track.pan().displayedValue().markInterested();
            track.isMonitoring().markInterested();
            track.monitorMode().markInterested();

            // Mark interest in send properties - only if send bank exists and has sends
            try {
                SendBank sendBank = track.sendBank();
                int sendBankSize = sendBank.getSizeOfBank();
                if (sendBankSize > 0) {
                    for (int sendIndex = 0; sendIndex < sendBankSize; sendIndex++) {
                        Send send = sendBank.getItemAt(sendIndex);
                        send.name().markInterested();
                        send.value().markInterested();
                        send.displayedValue().markInterested();
                        send.isEnabled().markInterested();
                    }
                }
            } catch (Exception e) {
                // Some tracks may not have send banks (e.g., master track)
            }

            ClipLauncherSlotBank trackSlots = track.clipLauncherSlotBank();
            for (int slotIndex = 0; slotIndex < trackSlots.getSizeOfBank(); slotIndex++) {
                ClipLauncherSlot slot = trackSlots.getItemAt(slotIndex);
                slot.hasContent().markInterested();
                slot.isPlaying().markInterested();
                slot.isRecording().markInterested();
                slot.isPlaybackQueued().markInterested();
                slot.isRecordingQueued().markInterested();
                slot.isStopQueued().markInterested();
                slot.color().markInterested();
                slot.name().markInterested();
            }
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Finds a track by name using case-sensitive matching.
     *
     * @param trackName The name of the track to find
     * @return Optional containing the track if found, empty otherwise
     */
    private Optional<Track> findTrackByName(String trackName) {
        if (trackName == null || trackName.trim().isEmpty()) {
            return Optional.empty();
        }

        for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
            Track track = trackBank.getItemAt(i);
            if (track.exists().get() && trackName.equals(track.name().get())) {
                return Optional.of(track);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a track by index.
     *
     * @param index The index of the track to find
     * @return Optional containing the track if found and exists, empty otherwise
     */
    private Optional<Track> findTrackByIndex(int index) {
        if (index < 0 || index >= trackBank.getSizeOfBank()) {
            return Optional.empty();
        }

        Track track = trackBank.getItemAt(index);
        return track.exists().get() ? Optional.of(track) : Optional.empty();
    }

    /**
     * Gets the index of a track by name.
     *
     * @param trackName The name of the track
     * @return The track index, or -1 if not found
     */
    private int getTrackIndexByName(String trackName) {
        if (trackName == null || trackName.trim().isEmpty()) {
            return -1;
        }

        for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
            Track track = trackBank.getItemAt(i);
            if (track.exists().get() && trackName.equals(track.name().get())) {
                return i;
            }
        }
        return -1;
    }

    // ========================================
    // Public API Methods
    // ========================================

    /**
     * Returns the number of tracks in the track bank.
     *
     * @return the size of the track bank
     */
    public int getTrackBankSize() {
        return trackBank.getSizeOfBank();
    }

    /**
     * Returns the name of the track at the given index.
     *
     * @param index the track index
     * @return the track name
     * @throws BitwigApiException if the index is invalid or track doesn't exist
     */
    public String getTrackNameByIndex(int index) throws BitwigApiException {
        final String operation = "getTrackNameByIndex";

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Validate track index
            if (index < 0 || index >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + index,
                    Map.of("index", index, "max_index", trackBank.getSizeOfBank() - 1)
                );
            }

            Track track = trackBank.getItemAt(index);
            if (!track.exists().get()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track at index " + index + " does not exist",
                    Map.of("index", index)
                );
            }

            return track.name().get();
        });
    }

    /**
     * Starts the transport playback.
     */
    public void startTransport() {
        logger.info("BitwigApiFacade: Starting transport playback");
        transport.play();
    }

    /**
     * Stops the transport playback.
     */
    public void stopTransport() {
        logger.info("BitwigApiFacade: Stopping transport playback");
        transport.stop();
    }

    /**
     * Get the ControllerHost instance.
     *
     * @return The ControllerHost
     */
    public ControllerHost getHost() {
        return host;
    }

    /**
     * Checks if a device is currently selected.
     *
     * @return true if a device is selected, false otherwise
     */
    public boolean isDeviceSelected() {
        logger.info("BitwigApiFacade: Checking if device is selected");
        return cursorDevice.exists().get();
    }

    /**
     * Gets the name of the currently selected device.
     *
     * @return The device name
     * @throws BitwigApiException if no device is selected
     */
    public String getSelectedDeviceName() throws BitwigApiException {
        final String operation = "getSelectedDeviceName";
        logger.info("BitwigApiFacade: Getting selected device name");

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (!isDeviceSelected()) {
                throw new BitwigApiException(
                    ErrorCode.DEVICE_NOT_SELECTED,
                    operation,
                    "No device is currently selected"
                );
            }
            return cursorDevice.name().get();
        });
    }

    /**
     * Gets the parameters of the currently selected device.
     *
     * @return A list of ParameterInfo objects representing all addressable parameters
     */
    public List<ParameterInfo> getSelectedDeviceParameters() {
        logger.info("BitwigApiFacade: Getting selected device parameters");
        List<ParameterInfo> parameters = new ArrayList<>();

        if (!isDeviceSelected()) {
            logger.info("BitwigApiFacade: No device selected, returning empty parameters list");
            return parameters;
        }

        for (int i = 0; i < deviceParameterBank.getParameterCount(); i++) {
            RemoteControl parameter = deviceParameterBank.getParameter(i);
            boolean exists = parameter.exists().get();

            if (exists) {
                String name = parameter.name().get();
                double value = parameter.value().get();
                String displayValue = parameter.displayedValue().get();

                // Handle null or empty names
                if (name != null && name.trim().isEmpty()) {
                    name = null;
                }

                parameters.add(new ParameterInfo(i, name, value, displayValue));
            }
        }

        logger.info("BitwigApiFacade: Retrieved " + parameters.size() + " parameters");
        return parameters;
    }

    /**
     * Sets the value of a specific parameter for the currently selected device.
     *
     * @param parameterIndex The index of the parameter to set (0 to parameterCount-1)
     * @param value          The value to set (0.0-1.0)
     * @throws BitwigApiException if parameterIndex is out of range, value is out of range, no device is selected, or Bitwig API error occurs
     */
    public void setSelectedDeviceParameter(int parameterIndex, double value) throws BitwigApiException {
        final String operation = "setSelectedDeviceParameter";
        logger.info("BitwigApiFacade: Setting parameter " + parameterIndex + " to " + value);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Check if device is selected
            if (!isDeviceSelected()) {
                throw new BitwigApiException(
                    ErrorCode.DEVICE_NOT_SELECTED,
                    operation,
                    "No device is currently selected"
                );
            }

            // Validate parameter index against actual parameter count
            int parameterCount = deviceParameterBank.getParameterCount();
            ParameterValidator.validateParameterIndex(parameterIndex, parameterCount, operation);

            // Validate value range
            ParameterValidator.validateParameterValue(value, operation);

            // Set the parameter value
            RemoteControl parameter = deviceParameterBank.getParameter(parameterIndex);
            parameter.value().set(value);

            logger.info("BitwigApiFacade: Successfully set parameter " + parameterIndex + " to " + value);
        });
    }

    /**
     * Finds a track by name using case-sensitive matching.
     *
     * @param trackName The name of the track to find
     * @return The track index if found
     * @throws BitwigApiException if the track is not found
     */
    public int findTrackIndexByName(String trackName) throws BitwigApiException {
        final String operation = "findTrackIndexByName";
        logger.info("BitwigApiFacade: Searching for track '" + trackName + "'");

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ParameterValidator.validateNotEmpty(trackName, "trackName", operation);

            int index = getTrackIndexByName(trackName);
            if (index == -1) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track '" + trackName + "' not found",
                    Map.of("trackName", trackName)
                );
            }

            logger.info("BitwigApiFacade: Found track '" + trackName + "' at index " + index);
            return index;
        });
    }

    /**
     * Checks if a track exists by name using case-sensitive matching.
     *
     * @param trackName The name of the track to check
     * @return true if the track exists, false otherwise
     */
    public boolean trackExists(String trackName) {
        try {
            findTrackIndexByName(trackName);
            return true;
        } catch (BitwigApiException e) {
            return false;
        }
    }

    /**
     * Gets the number of clip slots available for a track.
     *
     * @param trackName The name of the track
     * @return The number of clip slots, or 0 if track not found
     */
    public int getTrackClipCount(String trackName) {
        logger.info("BitwigApiFacade: Getting clip count for track '" + trackName + "'");

        Optional<Track> trackOpt = findTrackByName(trackName);
        if (trackOpt.isPresent()) {
            // Return the number of available clip launcher slots
            return trackOpt.get().clipLauncherSlotBank().getSizeOfBank();
        }

        logger.warn("BitwigApiFacade: Track '" + trackName + "' not found for clip count check");
        return 0;
    }

    /**
     * Launches a clip at the specified track and clip index.
     *
     * @param trackName The name of the track containing the clip
     * @param clipIndex The zero-based index of the clip slot to launch
     * @throws BitwigApiException if track is not found, clip index is invalid, or launch fails
     */
    public void launchClip(String trackName, int clipIndex) throws BitwigApiException {
        final String operation = "launchClip";
        logger.info("BitwigApiFacade: Launching clip at " + trackName + "[" + clipIndex + "]");

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Validate parameters
            ParameterValidator.validateNotEmpty(trackName, "trackName", operation);
            ParameterValidator.validateClipIndex(clipIndex, operation);

            // Find the track using helper method
            Optional<Track> trackOpt = findTrackByName(trackName);
            if (trackOpt.isEmpty()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track '" + trackName + "' not found",
                    Map.of("trackName", trackName)
                );
            }

            Track targetTrack = trackOpt.get();

            // Validate clip index within track bounds
            ClipLauncherSlotBank slotBank = targetTrack.clipLauncherSlotBank();
            if (clipIndex >= slotBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Clip index " + clipIndex + " out of bounds for track '" + trackName + "' (max: " + (slotBank.getSizeOfBank() - 1) + ")",
                    Map.of("trackName", trackName, "clipIndex", clipIndex, "maxIndex", slotBank.getSizeOfBank() - 1)
                );
            }

            // Launch the clip
            ClipLauncherSlot slot = slotBank.getItemAt(clipIndex);
            slot.launch();

            logger.info("BitwigApiFacade: Successfully launched clip at " + trackName + "[" + clipIndex + "]");
        });
    }

    /**
     * Finds the first scene index with the given name (case-sensitive).
     * Returns -1 if not found.
     */
    public int findSceneByName(String sceneName) {
        return sceneBankFacade.findSceneByName(sceneName);
    }

    /**
     * Gets the name of the scene at the given index, or null if not present.
     */
    public String getSceneName(int index) {
        return sceneBankFacade.getSceneName(index);
    }

    /**
     * Gets the number of scenes in the scene bank.
     */
    public int getSceneCount() {
        return sceneBankFacade.getSceneCount();
    }

    /**
     * Gets all scenes in the project with their details.
     *
     * @return A list of scene information maps containing index, name, and color
     */
    public List<Map<String, Object>> getAllScenesInfo() {
        logger.info("BitwigApiFacade: Getting all scenes info");
        return sceneBankFacade.getAllScenesInfo();
    }

    /**
     * Gets detailed clip slot information for a specific track and scene index.
     *
     * @param trackIndex The 0-based track index
     * @param trackName The name of the track
     * @param sceneIndex The 0-based scene index
     * @return Map containing detailed clip slot information
     */
    public Map<String, Object> getClipSlotDetails(int trackIndex, String trackName, int sceneIndex) {
        logger.info("BitwigApiFacade: Getting clip slot details for track " + trackIndex + " (" + trackName + ") at scene " + sceneIndex);

        Map<String, Object> slotInfo = new LinkedHashMap<>();

        try {
            // Get the track
            Track track = trackBank.getItemAt(trackIndex);
            if (!track.exists().get()) {
                return null; // Track doesn't exist
            }

            // Basic track information
            slotInfo.put("track_index", trackIndex);
            slotInfo.put("track_name", trackName);

            // Get the clip launcher slot at the scene index
            ClipLauncherSlotBank slotBank = track.clipLauncherSlotBank();
            if (sceneIndex >= slotBank.getSizeOfBank()) {
                // Scene index is beyond the available slots for this track
                return null;
            }

            ClipLauncherSlot slot = slotBank.getItemAt(sceneIndex);

            // Check if slot has content (marked as interested in constructor)
            boolean hasContent = slot.hasContent().get();
            slotInfo.put("has_content", hasContent);

            // Clip name (only if has content, marked as interested in constructor)
            String clipName = null;
            if (hasContent) {
                String name = slot.name().get();
                clipName = (name != null && name.trim().isEmpty()) ? null : name;
            }
            slotInfo.put("clip_name", clipName);

            // Clip color (only if has content, marked as interested in constructor)
            String clipColor = null;
            if (hasContent) {
                Color color = slot.color().get();
                if (color != null) {
                    clipColor = String.format("#%02X%02X%02X",
                        (int) (color.getRed() * 255),
                        (int) (color.getGreen() * 255),
                        (int) (color.getBlue() * 255));
                }
            }
            slotInfo.put("clip_color", clipColor);

            // Playback state flags (all properties marked as interested in constructor)
            slotInfo.put("is_playing", slot.isPlaying().get());
            slotInfo.put("is_recording", slot.isRecording().get());
            slotInfo.put("is_playback_queued", slot.isPlaybackQueued().get());
            slotInfo.put("is_recording_queued", slot.isRecordingQueued().get());
            slotInfo.put("is_stop_queued", slot.isStopQueued().get());

        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting clip slot details: " + e.getMessage());
            // Return basic structure with safe defaults
            slotInfo.put("track_index", trackIndex);
            slotInfo.put("track_name", trackName);
            slotInfo.put("has_content", false);
            slotInfo.put("clip_name", null);
            slotInfo.put("clip_color", null);
            slotInfo.put("is_playing", false);
            slotInfo.put("is_recording", false);
            slotInfo.put("is_playback_queued", false);
            slotInfo.put("is_recording_queued", false);
            slotInfo.put("is_stop_queued", false);
        }

        return slotInfo;
    }

    /**
     * Gets the current project name.
     *
     * @return The project name or "Unknown Project" if not available
     */
    public String getProjectName() {
        logger.info("BitwigApiFacade: Getting project name");
        String projectName = application.projectName().get();
        return projectName != null && !projectName.trim().isEmpty() ? projectName : Constants.DEFAULT_PROJECT_NAME;
    }

    /**
     * Checks if the audio engine is currently active.
     *
     * @return true if the audio engine is active, false otherwise
     */
    public boolean isAudioEngineActive() {
        logger.info("BitwigApiFacade: Checking audio engine status");
        return application.hasActiveEngine().get();
    }

    /**
     * Formats seconds into a time string in the format MM:SS.mmm or HH:MM:SS.mmm
     * @param seconds The time in seconds
     * @return Formatted time string with milliseconds
     */
    private String formatTimeString(double seconds) {
        try {
            int totalSeconds = (int) Math.floor(seconds);
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int secs = totalSeconds % 60;

            // Calculate milliseconds from the fractional part
            int milliseconds = (int) Math.round((seconds - Math.floor(seconds)) * 1000);

            // Handle edge case where rounding gives us 1000ms
            if (milliseconds >= 1000) {
                milliseconds = 0;
                secs += 1;
                if (secs >= 60) {
                    secs = 0;
                    minutes += 1;
                    if (minutes >= 60) {
                        minutes = 0;
                        hours += 1;
                    }
                }
            }

            if (hours > 0) {
                return String.format("%d:%02d:%02d.%03d", hours, minutes, secs, milliseconds);
            } else {
                return String.format("%d:%02d.%03d", minutes, secs, milliseconds);
            }
        } catch (Exception e) {
            return Constants.DEFAULT_TIME_STRING;
        }
    }

    /**
     * Gets the current transport status information.
     *
     * @return A map containing transport status data
     */
    public java.util.Map<String, Object> getTransportStatus() {
        logger.info("BitwigApiFacade: Getting transport status");
        java.util.Map<String, Object> transportMap = new java.util.LinkedHashMap<>();

        try {
            transportMap.put("playing", transport.isPlaying().get());
            transportMap.put("recording", transport.isArrangerRecordEnabled().get());
            transportMap.put("loop_active", transport.isArrangerLoopEnabled().get());
            transportMap.put("metronome_active", transport.isMetronomeEnabled().get());
            transportMap.put("current_tempo", transport.tempo().getRaw());
            transportMap.put("time_signature", transport.timeSignature().get());

            // Format position as Bitwig-style beat string
            double positionInBeats = transport.getPosition().get();
            String beatStr = formatBitwigBeatPosition(positionInBeats);
            transportMap.put("current_beat_str", beatStr);

            // Get time string using playPositionInSeconds
            double positionInSeconds = transport.playPositionInSeconds().get();
            String timeStr = formatTimeString(positionInSeconds);
            transportMap.put("current_time_str", timeStr);
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Unable to get complete transport status: " + e.getMessage());
            // Provide default values if API calls fail
            transportMap.put("playing", false);
            transportMap.put("recording", false);
            transportMap.put("loop_active", false);
            transportMap.put("metronome_active", false);
            transportMap.put("current_tempo", 120.0);
            transportMap.put("time_signature", "4/4");
            transportMap.put("current_beat_str", Constants.DEFAULT_BEAT_POSITION);
            transportMap.put("current_time_str", Constants.DEFAULT_TIME_STRING);
        }

        return transportMap;
    }

    /**
     * Formats a position in beats to Bitwig-style format: measures.beats.sixteenths:ticks
     * Example: 1.1.1:0 = measure 1, beat 1, sixteenth 1, tick 0
     */
    private String formatBitwigBeatPosition(double positionInBeats) {
        try {
            // Assume 4/4 time signature for calculation
            int beatsPerMeasure = Constants.BEATS_PER_MEASURE;
            int sixteenthsPerBeat = Constants.SIXTEENTHS_PER_BEAT;
            int ticksPerSixteenth = Constants.TICKS_PER_SIXTEENTH; // Common MIDI resolution

            // Convert beats to total ticks
            int totalTicks = (int) Math.round(positionInBeats * sixteenthsPerBeat * ticksPerSixteenth);

            // Calculate measures (1-based)
            int measures = (totalTicks / (beatsPerMeasure * sixteenthsPerBeat * ticksPerSixteenth)) + 1;
            int remainingTicks = totalTicks % (beatsPerMeasure * sixteenthsPerBeat * ticksPerSixteenth);

            // Calculate beats within measure (1-based)
            int beats = (remainingTicks / (sixteenthsPerBeat * ticksPerSixteenth)) + 1;
            remainingTicks = remainingTicks % (sixteenthsPerBeat * ticksPerSixteenth);

            // Calculate sixteenths within beat (1-based)
            int sixteenths = (remainingTicks / ticksPerSixteenth) + 1;
            int ticks = remainingTicks % ticksPerSixteenth;

            return String.format("%d.%d.%d:%d", measures, beats, sixteenths, ticks);
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error formatting beat position: " + e.getMessage());
            return Constants.DEFAULT_BEAT_POSITION;
        }
    }

    /**
     * Gets the project parameters from the project's remote controls page.
     * Only returns parameters where exists() is true.
     *
     * @return A list of ParameterInfo objects representing the existing project parameters
     */
    public List<ParameterInfo> getProjectParameters() {
        logger.info("BitwigApiFacade: Getting project parameters");
        List<ParameterInfo> parameters = new ArrayList<>();

        for (int i = 0; i < projectParameterBank.getParameterCount(); i++) {
            RemoteControl parameter = projectParameterBank.getParameter(i);
            boolean exists = parameter.exists().get();

            if (exists) {
                String name = parameter.name().get();
                double value = parameter.value().get();
                String displayValue = parameter.displayedValue().get();

                // Handle null or empty names
                if (name != null && name.trim().isEmpty()) {
                    name = null;
                }

                parameters.add(new ParameterInfo(i, name, value, displayValue));
            }
        }

        logger.info("BitwigApiFacade: Retrieved " + parameters.size() + " existing project parameters");
        return parameters;
    }

    /**
     * Gets information about the currently selected track.
     *
     * @return A map containing selected track information, or null if no track is selected
     */
    public Map<String, Object> getSelectedTrackInfo() {
        logger.info("BitwigApiFacade: Getting selected track information");

        if (!cursorTrack.exists().get()) {
            logger.info("BitwigApiFacade: No track selected");
            return null;
        }

        Map<String, Object> trackInfo = new LinkedHashMap<>();

        try {
            // Get track index by finding it in the track bank using helper method
            String trackName = cursorTrack.name().get();
            int trackIndex = getTrackIndexByName(trackName);

            trackInfo.put("index", trackIndex);
            trackInfo.put("name", trackName);
            trackInfo.put("type", cursorTrack.trackType().get().toLowerCase());
            trackInfo.put("is_group", cursorTrack.isGroup().get());
            trackInfo.put("muted", cursorTrack.mute().get());
            trackInfo.put("soloed", cursorTrack.solo().get());
            trackInfo.put("armed", cursorTrack.arm().get());

            logger.info("BitwigApiFacade: Retrieved selected track info: " + trackName);
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting selected track info: " + e.getMessage());
            return null;
        }

        return trackInfo;
    }

    /**
     * Gets information about the currently selected device including track context, device info, and parameters.
     *
     * @return A map containing selected device information, or null if no device is selected
     */
    public Map<String, Object> getSelectedDeviceInfo() {
        logger.info("BitwigApiFacade: Getting selected device information");

        if (!cursorDevice.exists().get()) {
            logger.info("BitwigApiFacade: No device selected");
            return null;
        }

        Map<String, Object> deviceInfo = new LinkedHashMap<>();

        try {
            // Get track information where the device is located
            String trackName = cursorTrack.name().get();
            int trackIndex = getTrackIndexByName(trackName);

            deviceInfo.put("track_name", trackName);
            deviceInfo.put("track_index", trackIndex);

            // Get device position/index in the device chain
            // Note: Bitwig API doesn't directly expose device index in chain, so we use 0 as default
            // This could be enhanced in the future with more complex logic to determine actual position
            deviceInfo.put("index", 0);

            // Get device name and bypass status
            deviceInfo.put("name", cursorDevice.name().get());
            deviceInfo.put("bypassed", !cursorDevice.isEnabled().get());

            // Get device parameters
            List<Map<String, Object>> parametersArray = new ArrayList<>();
            for (ParameterInfo p : getSelectedDeviceParameters()) {
                    Map<String, Object> paramMap = new LinkedHashMap<>();
                    paramMap.put("index", p.index());
                    paramMap.put("name", p.name());
                    paramMap.put("value", p.value());
                    paramMap.put("display_value", p.display_value());
                    parametersArray.add(paramMap);
                            }
            deviceInfo.put("parameters", parametersArray);

            logger.info("BitwigApiFacade: Retrieved selected device info: " + cursorDevice.name().get());
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting selected device info: " + e.getMessage());
            return null;
        }

        return deviceInfo;
    }

    /**
     * Gets a list of all tracks in the project with summary information.
     *
     * @param typeFilter Optional filter by track type (e.g., "audio", "instrument", "group", "effect", "master")
     * @return A list of track information maps
     */
    public List<Map<String, Object>> getAllTracksInfo(String typeFilter) {
        logger.info("BitwigApiFacade: Getting all tracks info" + (typeFilter != null ? " filtered by type: " + typeFilter : ""));
        List<Map<String, Object>> tracksInfo = new ArrayList<>();

        try {
            // Get selected track name for comparison
            String selectedTrackName = null;
            if (cursorTrack.exists().get()) {
                selectedTrackName = cursorTrack.name().get();
            }

            // Create parent track mapping to determine parent group indices
            Map<String, Integer> parentGroupMapping = buildParentGroupMapping();

            for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
                Track track = trackBank.getItemAt(i);
                if (!track.exists().get()) {
                    continue; // Skip non-existent tracks
                }

                Map<String, Object> trackInfo = new LinkedHashMap<>();

                // Basic track properties
                trackInfo.put("index", i);
                String trackName = track.name().get();
                trackInfo.put("name", trackName);

                String trackType = track.trackType().get().toLowerCase();
                trackInfo.put("type", trackType);

                // Apply type filter if specified
                if (typeFilter != null && !typeFilter.toLowerCase().equals(trackType)) {
                    continue;
                }

                trackInfo.put("is_group", track.isGroup().get());

                // Get parent group index from mapping
                trackInfo.put("parent_group_index", parentGroupMapping.get(trackName));

                // Get track activation status
                trackInfo.put("activated", track.isActivated().get());

                // Get track color and convert to RGB format
                trackInfo.put("color", formatTrackColor(track.color().get()));

                // Check if this track is selected
                boolean isSelected = selectedTrackName != null && selectedTrackName.equals(trackName);
                trackInfo.put("is_selected", isSelected);

                // Get devices on this track using the pre-existing device bank
                List<Map<String, Object>> devices = getTrackDevices(i);
                trackInfo.put("devices", devices);

                tracksInfo.add(trackInfo);
            }

            logger.info("BitwigApiFacade: Retrieved " + tracksInfo.size() + " tracks");
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting tracks info: " + e.getMessage());
        }

        return tracksInfo;
    }

    /**
     * Gets device information for a specific track by index.
     *
     * @param trackIndex The index of the track to get devices from
     * @return A list of device information maps
     */
    private List<Map<String, Object>> getTrackDevices(int trackIndex) {
        List<Map<String, Object>> devices = new ArrayList<>();

        try {
            // Use the pre-existing device bank for this track that was created in the constructor
            // and already has its properties marked as interested
            if (trackIndex < 0 || trackIndex >= trackDeviceBanks.size()) {
                logger.warn("BitwigApiFacade: Invalid track index for devices: " + trackIndex);
                return devices;
            }

            DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
            Track track = trackBank.getItemAt(trackIndex);

            // Create device info for each existing device
            for (int i = 0; i < deviceBank.getSizeOfBank(); i++) {
                Device device = deviceBank.getItemAt(i);

                // Check if device exists - this should work since markInterested() was called in constructor
                if (!device.exists().get()) {
                    continue;
                }

                Map<String, Object> deviceInfo = new LinkedHashMap<>();
                deviceInfo.put("index", i);

                // Get device name
                String deviceName = device.name().get();
                deviceInfo.put("name", deviceName);

                // Get device type
                String deviceType = device.deviceType().get();
                deviceInfo.put("type", deviceType);

                // Get device enabled status (bypassed = !enabled)
                boolean isEnabled = device.isEnabled().get();
                deviceInfo.put("bypassed", !isEnabled);

                devices.add(deviceInfo);
            }

            logger.info("BitwigApiFacade: Found " + devices.size() + " devices on track: " + track.name().get());

        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting devices for track index " + trackIndex + ": " + e.getMessage());
        }

        return devices;
    }

    /**
     * Builds a mapping of track names to their parent group track indices.
     * This creates parent track objects for each track to determine hierarchy.
     *
     * @return A map where keys are track names and values are parent group indices (null if no parent)
     */
    private Map<String, Integer> buildParentGroupMapping() {
        Map<String, Integer> parentMapping = new LinkedHashMap<>();

        try {
            for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
                Track track = trackBank.getItemAt(i);
                if (!track.exists().get()) {
                    continue;
                }

                String trackName = track.name().get();
                Integer parentGroupIndex = null;

                try {
                    // Create parent track object to check for parent group
                    Track parentTrack = track.createParentTrack(0, 0);
                    if (parentTrack != null && parentTrack.exists().get()) {
                        String parentName = parentTrack.name().get();

                        // Find the index of the parent track in our track bank
                        for (int j = 0; j < trackBank.getSizeOfBank(); j++) {
                            Track candidateParent = trackBank.getItemAt(j);
                            if (candidateParent.exists().get() &&
                                candidateParent.isGroup().get() &&
                                parentName.equals(candidateParent.name().get())) {
                                parentGroupIndex = j;
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("BitwigApiFacade: Error determining parent for track " + trackName + ": " + e.getMessage());
                }

                parentMapping.put(trackName, parentGroupIndex);
            }
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error building parent group mapping: " + e.getMessage());
        }

        return parentMapping;
    }

    /**
     * Gets detailed information about a track by absolute project index.
     */
    public Map<String, Object> getTrackDetailsByIndex(int index) throws BitwigApiException {
        final String operation = "get_track_details";
        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (index < 0 || index >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + index,
                    Map.of("index", index, "max_index", trackBank.getSizeOfBank() - 1)
                );
            }
            Track track = trackBank.getItemAt(index);
            if (!track.exists().get()) {
                throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation, "Track at index " + index + " does not exist", Map.of("index", index));
            }
            return buildDetailedTrackInfo(track, index);
        });
    }

    /**
     * Gets detailed information about a track by exact name (case-sensitive).
     */
    public Map<String, Object> getTrackDetailsByName(String trackName) throws BitwigApiException {
        final String operation = "get_track_details";
        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ParameterValidator.validateNotEmpty(trackName, "track_name", operation);
            int index = findTrackIndexByName(trackName);
            return getTrackDetailsByIndex(index);
        });
    }

    /**
     * Gets detailed information about the currently selected track, or null if none.
     */
    public Map<String, Object> getSelectedTrackDetails() {
        try {
            if (!cursorTrack.exists().get()) {
                return null;
            }
            String name = cursorTrack.name().get();
            // Find index in current bank for consistency
            int index = -1;
            for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
                Track t = trackBank.getItemAt(i);
                if (t.exists().get() && name.equals(t.name().get())) {
                    index = i;
                    break;
                }
            }
            // If not found in bank, attempt to build from cursor directly
            if (index >= 0) {
                return buildDetailedTrackInfo(trackBank.getItemAt(index), index);
            } else {
                // Build minimal from cursor and enrich where possible
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("index", -1);
                info.put("name", name);
                info.put("type", cursorTrack.trackType().get().toLowerCase());
                info.put("is_group", cursorTrack.isGroup().get());
                info.put("parent_group_index", null);
                info.put("activated", true);
                info.put("color", Constants.DEFAULT_COLOR);
                info.put("is_selected", true);
                info.put("devices", List.of());
                info.put("volume", cursorTrack.volume().value().get());
                info.put("volume_str", safeDisplay(cursorTrack.volume().displayedValue().get()));
                info.put("pan", cursorTrack.pan().value().get());
                info.put("pan_str", safeDisplay(cursorTrack.pan().displayedValue().get()));
                info.put("muted", cursorTrack.mute().get());
                info.put("soloed", cursorTrack.solo().get());
                info.put("armed", cursorTrack.arm().get());
                info.put("monitor_enabled", cursorTrack.isMonitoring().get());
                String mode = cursorTrack.monitorMode().get();
                boolean cursorAuto = mode != null && mode.toLowerCase().contains("auto");
                info.put("auto_monitor_enabled", cursorAuto);
                info.put("sends", List.of());
                info.put("clips", List.of());
                return info;
            }
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting selected track details: " + e.getMessage());
            return null;
        }
    }

    private String safeDisplay(String value) {
        return value != null ? value : "";
    }

    /**
     * Builds a detailed track info map including base fields, device summaries, channel params,
     * sends and clip launcher slots.
     */
    private Map<String, Object> buildDetailedTrackInfo(Track track, int index) {
        Map<String, Object> trackInfo = new LinkedHashMap<>();
        try {
            // Basic fields similar to getAllTracksInfo
            trackInfo.put("index", index);
            String trackName = track.name().get();
            trackInfo.put("name", trackName);
            String trackType = track.trackType().get().toLowerCase();
            trackInfo.put("type", trackType);
            trackInfo.put("is_group", track.isGroup().get());
            Map<String, Integer> parentMap = buildParentGroupMapping();
            trackInfo.put("parent_group_index", parentMap.get(trackName));
            trackInfo.put("activated", track.isActivated().get());
            trackInfo.put("color", formatTrackColor(track.color().get()));
            // Selected state
            boolean isSelected = cursorTrack.exists().get() && trackName.equals(cursorTrack.name().get());
            trackInfo.put("is_selected", isSelected);
            // Devices
            trackInfo.put("devices", getTrackDevices(index));

            // Channel parameters
            trackInfo.put("volume", track.volume().value().get());
            trackInfo.put("volume_str", safeDisplay(track.volume().displayedValue().get()));
            trackInfo.put("pan", track.pan().value().get());
            trackInfo.put("pan_str", safeDisplay(track.pan().displayedValue().get()));
            trackInfo.put("muted", track.mute().get());
            trackInfo.put("soloed", track.solo().get());
            trackInfo.put("armed", track.arm().get());
            // Monitoring (properties marked as interested in constructor)
            boolean monitoring = track.isMonitoring().get();
            String mode = track.monitorMode().get();
            boolean autoMon = mode != null && mode.toLowerCase().contains("auto");
            trackInfo.put("monitor_enabled", monitoring);
            trackInfo.put("auto_monitor_enabled", autoMon);

            // Sends
            List<Map<String, Object>> sends = new ArrayList<>();
            try {
                SendBank sendBank = track.sendBank();
                int sendCount = sendBank.getSizeOfBank();
                for (int i = 0; i < sendCount; i++) {
                    Send send = sendBank.getItemAt(i);
                    Map<String, Object> sendMap = new LinkedHashMap<>();
                    sendMap.put("name", send.name().get());
                    sendMap.put("volume", send.value().get());
                    sendMap.put("volume_str", safeDisplay(send.displayedValue().get()));
                    sendMap.put("activated", send.isEnabled().get());
                    sends.add(sendMap);
                }
            } catch (Exception e) {
                logger.warn("BitwigApiFacade: Error reading sends for track " + trackName + ": " + e.getMessage());
            }
            trackInfo.put("sends", sends);

            // Clips
            List<Map<String, Object>> clips = new ArrayList<>();
            try {
                ClipLauncherSlotBank slotBank = track.clipLauncherSlotBank();
                int slots = slotBank.getSizeOfBank();
                for (int s = 0; s < slots; s++) {
                    ClipLauncherSlot slot = slotBank.getItemAt(s);
                    Map<String, Object> slotMap = new LinkedHashMap<>();
                    slotMap.put("slot_index", s);
                    // Scene name from scene bank facade
                    String sceneName = getSceneName(s);
                    slotMap.put("scene_name", sceneName);
                    boolean hasContent = false;
                    try { hasContent = slot.hasContent().get(); } catch (Exception ignored) {}
                    slotMap.put("has_content", hasContent);

                    // Clip name from slot name value if available
                    String clipName = null;
                    try {
                        clipName = slot.name().get();
                        if (clipName != null && clipName.trim().isEmpty()) clipName = null;
                    } catch (Exception ignored) {}
                    slotMap.put("clip_name", clipName);
                    try {
                        Color c = slot.color().get();
                        slotMap.put("clip_color", c != null ? formatTrackColor(c) : null);
                    } catch (Exception e) {
                        slotMap.put("clip_color", null);
                    }
                    // Removed unsupported length / is_looping fields

                    // Playback state flags
                    try { slotMap.put("is_playing", slot.isPlaying().get()); } catch (Exception e) { slotMap.put("is_playing", null); }
                    try { slotMap.put("is_recording", slot.isRecording().get()); } catch (Exception e) { slotMap.put("is_recording", null); }
                    try { slotMap.put("is_playback_queued", slot.isPlaybackQueued().get()); } catch (Exception e) { slotMap.put("is_playback_queued", null); }

                    clips.add(slotMap);
                }
            } catch (Exception e) {
                logger.warn("BitwigApiFacade: Error reading clip slots for track " + trackName + ": " + e.getMessage());
            }
            trackInfo.put("clips", clips);
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error building detailed track info: " + e.getMessage());
        }
        return trackInfo;
    }

    /**
     * Formats a ColorValue object into an RGB string format.
     */
    private String formatTrackColor(Color colorValue) {
        try {
            return String.format("rgb(%d,%d,%d)",
                (int) (colorValue.getRed() * 255),
                (int) (colorValue.getGreen() * 255),
                (int) (colorValue.getBlue() * 255));

        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error formatting track color: " + e.getMessage());
            return Constants.DEFAULT_COLOR; // Default gray fallback
        }
    }

    /**
     * Gets detailed device information for a specific track identified by index, name, or selected track.
     *
     * @param trackIndex The 0-based track index (optional)
     * @param trackName The exact track name (optional)
     * @param getSelected Whether to get devices for the selected track (optional)
     * @return List of device summary objects with detailed information
     * @throws BitwigApiException if the track is not found or API access fails
     */
    public List<Map<String, Object>> getDevicesOnTrack(Integer trackIndex, String trackName, Boolean getSelected)
            throws BitwigApiException {
        final String operation = "getDevicesOnTrack";

        try {
            Track targetTrack = null;
            int resolvedTrackIndex = -1;

            // Resolve target track based on parameters
            if (trackIndex != null) {
                // Track by index
                if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                    throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                        "Track index " + trackIndex + " is out of range [0, " + (trackBank.getSizeOfBank() - 1) + "]");
                }

                targetTrack = trackBank.getItemAt(trackIndex);
                if (!targetTrack.exists().get()) {
                    throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                        "Track at index " + trackIndex + " does not exist");
                }
                resolvedTrackIndex = trackIndex;

            } else if (trackName != null) {
                // Track by name - find exact match
                for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
                    Track track = trackBank.getItemAt(i);
                    if (track.exists().get() && trackName.equals(track.name().get())) {
                        targetTrack = track;
                        resolvedTrackIndex = i;
                        break;
                    }
                }

                if (targetTrack == null) {
                    throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                        "No track found with name '" + trackName + "'");
                }

            } else if (Boolean.TRUE.equals(getSelected)) {
                // Use selected track (cursor track)
                if (!cursorTrack.exists().get()) {
                    throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                        "No track is currently selected");
                }

                // Find the index of the cursor track in the track bank
                String selectedTrackName = cursorTrack.name().get();
                for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
                    Track track = trackBank.getItemAt(i);
                    if (track.exists().get() && selectedTrackName.equals(track.name().get())) {
                        targetTrack = track;
                        resolvedTrackIndex = i;
                        break;
                    }
                }

                if (targetTrack == null) {
                    throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                        "Selected track not found in track bank");
                }
            }

            if (targetTrack == null) {
                throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                    "No valid track identifier provided");
            }

            // Get devices for the resolved track
            return getDetailedTrackDevices(resolvedTrackIndex, targetTrack);

        } catch (BitwigApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("BitwigApiFacade: Unexpected error in " + operation + ": " + e.getMessage());
            throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, operation,
                "Failed to get devices for track: " + e.getMessage());
        }
    }

    /**
     * Gets detailed device information for a specific track with enhanced device details.
     *
     * @param trackIndex The resolved track index
     * @param track The target track object
     * @return List of detailed device information maps
     */
    private List<Map<String, Object>> getDetailedTrackDevices(int trackIndex, Track track) {
        List<Map<String, Object>> devices = new ArrayList<>();

        try {
            // Use the pre-existing device bank for this track
            if (trackIndex < 0 || trackIndex >= trackDeviceBanks.size()) {
                logger.warn("BitwigApiFacade: Invalid track index for devices: " + trackIndex);
                return devices;
            }

            DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);

            // Get cursor device info for selection comparison (only if we have a selected track and device)
            String selectedDeviceName = null;
            boolean isSelectedTrack = cursorTrack.exists().get() && track.name().get().equals(cursorTrack.name().get());
            if (isSelectedTrack && cursorDevice.exists().get()) {
                selectedDeviceName = cursorDevice.name().get();
            }

            // Iterate through device bank with proper enumeration
            for (int i = 0; i < deviceBank.getSizeOfBank(); i++) {
                Device device = deviceBank.getItemAt(i);

                // Check if device exists
                if (!device.exists().get()) {
                    continue;
                }

                Map<String, Object> deviceInfo = new LinkedHashMap<>();
                deviceInfo.put("index", i);

                // Get device name
                String deviceName = device.name().get();
                deviceInfo.put("name", deviceName);

                // Get and map device type
                String rawDeviceType = device.deviceType().get();
                String mappedType = mapDeviceType(rawDeviceType);
                deviceInfo.put("type", mappedType);

                // Get device bypassed status (bypassed = !enabled)
                boolean isEnabled = device.isEnabled().get();
                deviceInfo.put("bypassed", !isEnabled);

                // Determine if this device is selected
                boolean isDeviceSelected = false;
                if (isSelectedTrack && selectedDeviceName != null) {
                    // Use name matching for device selection comparison
                    isDeviceSelected = deviceName.equals(selectedDeviceName);
                }
                deviceInfo.put("is_selected", isDeviceSelected);

                // Optional UI state fields - only include if available
                // Per story requirements, omit these fields if not available from API
                // deviceInfo.put("is_expanded", null);  // Omitted - not available from Controller API
                // deviceInfo.put("is_window_open", null);  // Omitted - not available from Controller API

                devices.add(deviceInfo);
            }

            logger.info("BitwigApiFacade: Found " + devices.size() + " devices on track: " + track.name().get());

        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting detailed devices for track index " + trackIndex + ": " + e.getMessage());
        }

        return devices;
    }

    /**
     * Maps Bitwig device types to standardized type names.
     *
     * @param rawDeviceType The raw device type from Bitwig API
     * @return Mapped device type: "Instrument", "AudioFX", "NoteFX", or "Unknown"
     */
    private String mapDeviceType(String rawDeviceType) {
        if (rawDeviceType == null) {
            return "Unknown";
        }

        String lowerType = rawDeviceType.toLowerCase();

        if (lowerType.contains("instrument")) {
            return "Instrument";
        } else if (lowerType.contains("note") || lowerType.contains("midi")) {
            return "NoteFX";
        } else if (lowerType.contains("audio") || lowerType.contains("effect") || lowerType.contains("fx")) {
            return "AudioFX";
        } else {
            return "Unknown";
        }
    }

    /**
     * Gets detailed device information including remote controls and pages.
     *
     * @param trackIndex The track index (nullable)
     * @param trackName The track name (nullable)
     * @param deviceIndex The device index (nullable)
     * @param deviceName The device name (nullable)
     * @param getForSelectedDevice Whether to get selected device (nullable)
     * @return DeviceDetailsResult containing complete device information
     * @throws BitwigApiException if device/track not found or parameters invalid
     */
    public io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult getDeviceDetails(
            Integer trackIndex, String trackName, Integer deviceIndex, String deviceName, Boolean getForSelectedDevice)
            throws BitwigApiException {
        final String operation = "getDeviceDetails";

        try {
            // Determine operation mode
            boolean isSelectedDeviceMode = Boolean.TRUE.equals(getForSelectedDevice) ||
                (trackIndex == null && trackName == null && deviceIndex == null && deviceName == null);

            if (isSelectedDeviceMode) {
                return getSelectedDeviceDetails();
            } else {
                return getTargetDeviceDetails(trackIndex, trackName, deviceIndex, deviceName);
            }

        } catch (BitwigApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("BitwigApiFacade: Unexpected error in " + operation + ": " + e.getMessage());
            throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, operation,
                "Failed to get device details: " + e.getMessage());
        }
    }

    /**
     * Gets details for the currently selected device.
     */
    private io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult getSelectedDeviceDetails()
            throws BitwigApiException {
        final String operation = "getSelectedDeviceDetails";

        // Check if device is selected
        if (!cursorDevice.exists().get()) {
            throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, operation,
                "No device is currently selected");
        }

        // Check if cursor track exists
        if (!cursorTrack.exists().get()) {
            throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                "No track is currently selected");
        }

        // Get track index directly from cursor track position
        int resolvedTrackIndex = cursorTrack.position().get();
        String selectedTrackName = cursorTrack.name().get();

        // Verify the position is within our track bank range
        if (resolvedTrackIndex < 0 || resolvedTrackIndex >= trackBank.getSizeOfBank()) {
            throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                "Selected track position " + resolvedTrackIndex + " is outside track bank range [0, " + (trackBank.getSizeOfBank() - 1) + "]");
        }

        // Get device basic properties
        String deviceName = cursorDevice.name().get();
        String rawDeviceType = cursorDevice.deviceType().get();
        String mappedType = mapDeviceType(rawDeviceType);
        boolean isEnabled = cursorDevice.isEnabled().get();
        boolean isBypassed = !isEnabled;

        // Find device index by comparing with devices in the track
        int deviceIndex = findDeviceIndexInTrack(resolvedTrackIndex, deviceName);

        // Get remote controls for the currently selected page
        List<ParameterInfo> remoteControls = getDeviceRemoteControlsFromCursor();

        return new io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult(
            resolvedTrackIndex,
            selectedTrackName,
            deviceIndex,
            deviceName,
            mappedType,
            isBypassed,
            true, // is_selected = true since this is the selected device
            remoteControls
        );
    }

    /**
     * Gets details for a device specified by track and device identifiers.
     */
    private io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult getTargetDeviceDetails(
            Integer trackIndex, String trackName, Integer deviceIndex, String deviceName)
            throws BitwigApiException {
        final String operation = "getTargetDeviceDetails";

        // Resolve target track
        Track targetTrack;
        int resolvedTrackIndex;

        if (trackIndex != null) {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                    "Track index " + trackIndex + " is out of range [0, " + (trackBank.getSizeOfBank() - 1) + "]");
            }
            Optional<Track> trackOpt = findTrackByIndex(trackIndex);
            if (trackOpt.isEmpty()) {
                throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                    "Track at index " + trackIndex + " does not exist");
            }
            targetTrack = trackOpt.get();
            resolvedTrackIndex = trackIndex;
        } else if (trackName != null) {
            Optional<Track> trackOpt = findTrackByName(trackName);
            if (trackOpt.isEmpty()) {
                throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                    "No track found with name '" + trackName + "'");
            }
            targetTrack = trackOpt.get();
            resolvedTrackIndex = getTrackIndexByName(trackName);
        } else {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                "Either trackIndex or trackName must be provided");
        }

        // Resolve target device
        DeviceBank deviceBank = trackDeviceBanks.get(resolvedTrackIndex);
        Device targetDevice = null;
        int resolvedDeviceIndex = -1;

        if (deviceIndex != null) {
            if (deviceIndex < 0 || deviceIndex >= deviceBank.getSizeOfBank()) {
                throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                    "Device index " + deviceIndex + " is out of range [0, " + (deviceBank.getSizeOfBank() - 1) + "]");
            }
            targetDevice = deviceBank.getItemAt(deviceIndex);
            if (!targetDevice.exists().get()) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_FOUND, operation,
                    "Device at index " + deviceIndex + " does not exist on track");
            }
            resolvedDeviceIndex = deviceIndex;
        } else if (deviceName != null) {
            for (int i = 0; i < deviceBank.getSizeOfBank(); i++) {
                Device device = deviceBank.getItemAt(i);
                if (device.exists().get() && deviceName.equals(device.name().get())) {
                    targetDevice = device;
                    resolvedDeviceIndex = i;
                    break;
                }
            }
            if (targetDevice == null) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_FOUND, operation,
                    "No device found with name '" + deviceName + "' on track");
            }
        } else {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                "Either deviceIndex or deviceName must be provided");
        }

        // Get device basic properties
        String actualDeviceName = targetDevice.name().get();
        String rawDeviceType = targetDevice.deviceType().get();
        String mappedType = mapDeviceType(rawDeviceType);
        boolean isEnabled = targetDevice.isEnabled().get();
        boolean isBypassed = !isEnabled;

        // Determine if this device is selected by comparing with cursor device
        boolean isSelected = isDeviceSelectedComparison(resolvedTrackIndex, targetTrack.name().get(),
                                                       resolvedDeviceIndex, actualDeviceName);

        // For non-selected devices, remote control access is limited
        List<ParameterInfo> remoteControls = getDeviceRemoteControlsFromDevice(targetDevice);

        return new io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult(
            resolvedTrackIndex,
            targetTrack.name().get(),
            resolvedDeviceIndex,
            actualDeviceName,
            mappedType,
            isBypassed,
            isSelected,
            remoteControls
        );
    }

    /**
     * Gets remote controls from the cursor device (selected device).
     *
     * This directly returns the existing device parameters since they represent
     * the same data (remote controls for the currently selected page).
     */
    private List<ParameterInfo> getDeviceRemoteControlsFromCursor() {
        // Direct access to selected device parameters - no conversion needed
        return getSelectedDeviceParameters();
    }

    /**
     * Gets remote controls from a specific device (non-cursor).
     *
     * Note: The Bitwig Controller API does not easily expose remote controls
     * for non-selected devices without temporarily selecting them, which would
     * disrupt the user experience. Therefore, this method returns an empty list.
     */
    private List<ParameterInfo> getDeviceRemoteControlsFromDevice(Device device) {
        // Limitation: Bitwig Controller API does not provide easy access to
        // remote controls for non-selected devices
        return new ArrayList<>();
    }

    /**
     * Finds the index of a device in a track by comparing names.
     */
    private int findDeviceIndexInTrack(int trackIndex, String deviceName) {
        if (trackIndex < 0 || trackIndex >= trackDeviceBanks.size()) {
            return -1;
        }

        DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
        for (int i = 0; i < deviceBank.getSizeOfBank(); i++) {
            Device device = deviceBank.getItemAt(i);
            if (device.exists().get() && deviceName.equals(device.name().get())) {
                return i;
            }
        }
        return -1; // Not found
    }

    /**
     * Determines if a device is selected by comparing with cursor device.
     */
    private boolean isDeviceSelectedComparison(int trackIndex, String trackName, int deviceIndex, String deviceName) {
        // Check if cursor device exists
        if (!cursorDevice.exists().get() || !cursorTrack.exists().get()) {
            return false;
        }

        // Compare track
        String selectedTrackName = cursorTrack.name().get();
        if (!trackName.equals(selectedTrackName)) {
            return false;
        }

        // Compare device name
        String selectedDeviceName = cursorDevice.name().get();
        return deviceName.equals(selectedDeviceName);
    }

    // ========================================
    // Device Selection & Preset Navigation
    // ========================================

    /**
     * Finds the index of the current preset by matching its name.
     */
    private int findPresetIndex() {
        try {
            String name = cursorDevice.presetName().get();
            if (name != null && presetNames != null) {
                for (int i = 0; i < presetNames.length; i++) {
                    if (name.equals(presetNames[i])) return i;
                }
            }
        } catch (Exception e) { /* ignore */ }
        return -1;
    }

    /**
     * Selects a device on a track by navigating the cursor device to it.
     */
    public Map<String, Object> selectDeviceOnTrack(String trackName, int deviceIndex) throws BitwigApiException {
        final String operation = "selectDevice";
        logger.info("BitwigApiFacade: Selecting device " + deviceIndex + " on track '" + trackName + "'");

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ParameterValidator.validateNotEmpty(trackName, "trackName", operation);

            Optional<Track> trackOpt = findTrackByName(trackName);
            if (trackOpt.isEmpty()) {
                throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                    "Track '" + trackName + "' not found", Map.of("trackName", trackName));
            }
            int trackIdx = getTrackIndexByName(trackName);

            if (trackIdx < 0 || trackIdx >= trackDeviceBanks.size()) {
                throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                    "Device bank not available for track index " + trackIdx, Map.of("trackIndex", trackIdx));
            }

            DeviceBank deviceBank = trackDeviceBanks.get(trackIdx);
            if (deviceIndex < 0 || deviceIndex >= deviceBank.getSizeOfBank()) {
                throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                    "Device index " + deviceIndex + " out of bounds (max: " + (deviceBank.getSizeOfBank() - 1) + ")",
                    Map.of("deviceIndex", deviceIndex, "maxIndex", deviceBank.getSizeOfBank() - 1));
            }

            Device device = deviceBank.getItemAt(deviceIndex);
            if (!device.exists().get()) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, operation,
                    "Device at index " + deviceIndex + " does not exist", Map.of("deviceIndex", deviceIndex));
            }

            device.selectInEditor();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", "device_selected");
            result.put("track_name", trackName);
            result.put("device_index", deviceIndex);
            result.put("device_name", device.name().get());
            result.put("device_type", device.deviceType().get());
            result.put("preset_name", cursorDevice.presetName().get());
            result.put("preset_category", cursorDevice.presetCategory().get());
            result.put("preset_creator", cursorDevice.presetCreator().get());

            logger.info("BitwigApiFacade: Selected device '" + device.name().get() + "' on " + trackName);
            return result;
        });
    }

    /**
     * Returns all cached preset/page information for the cursor device.
     */
    public Map<String, Object> getDevicePresets() throws BitwigApiException {
        final String operation = "getDevicePresets";
        logger.info("BitwigApiFacade: Getting device presets");

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (!cursorDevice.exists().get()) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, operation, "No device selected");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("device_name", cursorDevice.name().get());
            result.put("device_type", cursorDevice.deviceType().get());
            result.put("preset_name", cursorDevice.presetName().get());
            result.put("preset_category", cursorDevice.presetCategory().get());
            result.put("preset_creator", cursorDevice.presetCreator().get());
            result.put("preset_index", selectedPresetIndex);
            result.put("preset_count", presetNames != null ? presetNames.length : 0);
            result.put("preset_names", presetNames);
            result.put("preset_categories", presetCategories);
            result.put("preset_creators", presetCreators);
            result.put("page_index", selectedPageIndex);
            result.put("page_count", pageNames != null ? pageNames.length : 0);
            result.put("page_names", pageNames);

            return result;
        });
    }

    /**
     * Loads a preset by index on the cursor device.
     */
    public Map<String, Object> loadPreset(int presetIndex) throws BitwigApiException {
        final String operation = "loadPreset";
        logger.info("BitwigApiFacade: Loading preset " + presetIndex);

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (!cursorDevice.exists().get()) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, operation, "No device selected");
            }
            if (presetNames == null || presetIndex < 0 || presetIndex >= presetNames.length) {
                throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                    "Preset index " + presetIndex + " out of range (0-" + (presetNames != null ? presetNames.length - 1 : 0) + ")",
                    Map.of("presetIndex", presetIndex));
            }

            cursorDevice.loadPreset(presetIndex);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", "preset_loaded");
            result.put("preset_index", presetIndex);
            result.put("preset_name", presetNames[presetIndex]);
            if (presetCategories != null && presetIndex < presetCategories.length) {
                result.put("preset_category", presetCategories[presetIndex]);
            }

            logger.info("BitwigApiFacade: Loaded preset '" + presetNames[presetIndex] + "'");
            return result;
        });
    }

    /**
     * Navigates presets (next/previous) on the cursor device.
     */
    public Map<String, Object> navigatePreset(String direction) throws BitwigApiException {
        final String operation = "navigatePreset";
        logger.info("BitwigApiFacade: Navigating preset " + direction);

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (!cursorDevice.exists().get()) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, operation, "No device selected");
            }

            if ("next".equalsIgnoreCase(direction)) {
                cursorDevice.switchToNextPreset();
            } else if ("previous".equalsIgnoreCase(direction)) {
                cursorDevice.switchToPreviousPreset();
            } else {
                throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                    "Direction must be 'next' or 'previous', got: " + direction, Map.of("direction", direction));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", "preset_navigated");
            result.put("direction", direction);
            result.put("preset_name", cursorDevice.presetName().get());
            result.put("preset_category", cursorDevice.presetCategory().get());

            return result;
        });
    }

    /**
     * Sets the parameter page on the cursor device.
     */
    public Map<String, Object> selectDevicePage(int pageIndex) throws BitwigApiException {
        final String operation = "selectDevicePage";
        logger.info("BitwigApiFacade: Selecting device page " + pageIndex);

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (!cursorDevice.exists().get()) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, operation, "No device selected");
            }
            if (pageNames == null || pageIndex < 0 || pageIndex >= pageNames.length) {
                throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                    "Page index " + pageIndex + " out of range (0-" + (pageNames != null ? pageNames.length - 1 : 0) + ")",
                    Map.of("pageIndex", pageIndex));
            }

            cursorDevice.setParameterPage(pageIndex);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", "page_selected");
            result.put("page_index", pageIndex);
            result.put("page_name", pageNames[pageIndex]);

            return result;
        });
    }

    /**
     * Navigates preset categories (next/previous) on the cursor device.
     */
    public Map<String, Object> navigatePresetCategory(String direction) throws BitwigApiException {
        final String operation = "navigatePresetCategory";
        logger.info("BitwigApiFacade: Navigating preset category " + direction);

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (!cursorDevice.exists().get()) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, operation, "No device selected");
            }
            if ("next".equalsIgnoreCase(direction)) {
                cursorDevice.switchToNextPresetCategory();
            } else if ("previous".equalsIgnoreCase(direction)) {
                cursorDevice.switchToPreviousPresetCategory();
            } else {
                throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                    "Direction must be 'next' or 'previous'", Map.of("direction", direction));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", "preset_category_navigated");
            result.put("direction", direction);
            result.put("preset_category", cursorDevice.presetCategory().get());
            return result;
        });
    }

    // ========================================
    // Clip Content Creation Methods
    // ========================================

    /**
     * Gets a ClipLauncherSlot by track name and slot index.
     *
     * @param trackName The name of the track
     * @param slotIndex The zero-based clip launcher slot index (scene index)
     * @return The ClipLauncherSlot
     * @throws BitwigApiException if track not found or slot index out of bounds
     */
    private ClipLauncherSlot getClipLauncherSlot(String trackName, int slotIndex) throws BitwigApiException {
        final String operation = "getClipLauncherSlot";

        ParameterValidator.validateNotEmpty(trackName, "trackName", operation);
        ParameterValidator.validateClipIndex(slotIndex, operation);

        Optional<Track> trackOpt = findTrackByName(trackName);
        if (trackOpt.isEmpty()) {
            throw new BitwigApiException(
                ErrorCode.TRACK_NOT_FOUND,
                operation,
                "Track '" + trackName + "' not found",
                Map.of("trackName", trackName)
            );
        }

        ClipLauncherSlotBank slotBank = trackOpt.get().clipLauncherSlotBank();
        if (slotIndex >= slotBank.getSizeOfBank()) {
            throw new BitwigApiException(
                ErrorCode.INVALID_RANGE,
                operation,
                "Slot index " + slotIndex + " out of bounds for track '" + trackName + "' (max: " + (slotBank.getSizeOfBank() - 1) + ")",
                Map.of("trackName", trackName, "slotIndex", slotIndex, "maxIndex", slotBank.getSizeOfBank() - 1)
            );
        }

        return slotBank.getItemAt(slotIndex);
    }

    /**
     * Creates an empty MIDI clip in a launcher slot.
     *
     * @param trackName     The name of the track
     * @param slotIndex     The zero-based slot/scene index
     * @param lengthInBeats The length of the new clip in beats (quarter notes)
     * @throws BitwigApiException if track not found, slot out of bounds, or API error
     */
    public void createClipOnTrack(String trackName, int slotIndex, int lengthInBeats) throws BitwigApiException {
        final String operation = "createClip";
        logger.info("BitwigApiFacade: Creating clip on track '" + trackName + "' slot " + slotIndex + " length " + lengthInBeats);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ClipLauncherSlot slot = getClipLauncherSlot(trackName, slotIndex);

            // Validate length
            if (lengthInBeats < 1 || lengthInBeats > 4096) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER,
                    operation,
                    "Clip length must be between 1 and 4096 beats, got: " + lengthInBeats,
                    Map.of("lengthInBeats", lengthInBeats)
                );
            }

            // Create the clip (overwrites existing clip in this slot)
            slot.createEmptyClip(lengthInBeats);

            logger.info("BitwigApiFacade: Successfully created clip on " + trackName + "[" + slotIndex + "] (" + lengthInBeats + " beats)");
        });
    }

    /**
     * Deletes a clip from a launcher slot.
     *
     * @param trackName The name of the track
     * @param slotIndex The zero-based slot/scene index
     * @throws BitwigApiException if track not found, slot out of bounds, or API error
     */
    public void deleteClipOnTrack(String trackName, int slotIndex) throws BitwigApiException {
        final String operation = "deleteClip";
        logger.info("BitwigApiFacade: Deleting clip on track '" + trackName + "' slot " + slotIndex);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ClipLauncherSlot slot = getClipLauncherSlot(trackName, slotIndex);
            slot.deleteObject();

            logger.info("BitwigApiFacade: Successfully deleted clip on " + trackName + "[" + slotIndex + "]");
        });
    }

    /**
     * Adds a single MIDI note to a clip by first selecting the slot, then using
     * CursorClip to set a step at the given key/step position.
     *
     * @param trackName The name of the track
     * @param slotIndex The zero-based slot/scene index
     * @param key       MIDI key (0-127)
     * @param step      Step position within the clip (0-based, 16th notes)
     * @param velocity  Note velocity (0.0-1.0)
     * @param duration  Note duration in beats (e.g. 0.25 = 16th note)
     * @throws BitwigApiException if parameters are invalid or API error
     */
    public void addNoteToClip(String trackName, int slotIndex, int key, int step, double velocity, double duration) throws BitwigApiException {
        final String operation = "addNote";
        logger.info("BitwigApiFacade: Adding note to " + trackName + "[" + slotIndex + "] key=" + key + " step=" + step + " vel=" + velocity + " dur=" + duration);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ClipLauncherSlot slot = getClipLauncherSlot(trackName, slotIndex);

            // Validate note parameters
            if (key < 0 || key > 127) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER,
                    operation,
                    "MIDI key must be between 0 and 127, got: " + key,
                    Map.of("key", key)
                );
            }
            if (step < 0 || step > 127) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER,
                    operation,
                    "Step must be between 0 and 127, got: " + step,
                    Map.of("step", step)
                );
            }
            if (velocity < 0.0 || velocity > 1.0) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER,
                    operation,
                    "Velocity must be between 0.0 and 1.0, got: " + velocity,
                    Map.of("velocity", velocity)
                );
            }
            if (duration <= 0.0 || duration > 128.0) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER,
                    operation,
                    "Duration must be between 0.0 and 128.0 beats, got: " + duration,
                    Map.of("duration", duration)
                );
            }

            // Select the slot so the cursor clip points to it
            slot.select();

            // Use CursorClip to set the step
            cursorClip.scrollToKey(key);
            cursorClip.scrollToStep(step);
            cursorClip.setStep(key, step, (int)(velocity * 127), duration);

            logger.info("BitwigApiFacade: Successfully added note to " + trackName + "[" + slotIndex + "] key=" + key + " step=" + step);
        });
    }

    /**
     * Clears all notes/steps from a clip in a launcher slot.
     *
     * @param trackName The name of the track
     * @param slotIndex The zero-based slot/scene index
     * @throws BitwigApiException if track not found, slot out of bounds, or API error
     */
    public void clearSlotOnTrack(String trackName, int slotIndex) throws BitwigApiException {
        final String operation = "clearSlot";
        logger.info("BitwigApiFacade: Clearing slot on track '" + trackName + "' slot " + slotIndex);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ClipLauncherSlot slot = getClipLauncherSlot(trackName, slotIndex);

            // Select the slot so the cursor clip points to it
            slot.select();

            // Clear all steps in the clip
            cursorClip.clearSteps();

            logger.info("BitwigApiFacade: Successfully cleared slot " + trackName + "[" + slotIndex + "]");
        });
    }

    // ========================================
    // Clip Reading Methods
    // ========================================

    /**
     * Selects a clip launcher slot, making it the active cursor clip.
     */
    public Map<String, Object> selectClipSlot(String trackName, int slotIndex) throws BitwigApiException {
        final String operation = "selectClipSlot";
        logger.info("BitwigApiFacade: Selecting clip slot " + trackName + "[" + slotIndex + "]");

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ClipLauncherSlot slot = getClipLauncherSlot(trackName, slotIndex);
            slot.select();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", "clip_selected");
            result.put("track_name", trackName);
            result.put("slot_index", slotIndex);
            result.put("has_content", slot.hasContent().get());
            if (slot.hasContent().get()) {
                result.put("clip_name", slot.name().get());
            }

            logger.info("BitwigApiFacade: Selected clip slot " + trackName + "[" + slotIndex + "]");
            return result;
        });
    }

    /**
     * Reads all MIDI notes from a clip in a launcher slot.
     * Returns up to 500 notes from the first 64 steps × 128 keys.
     */
    public Map<String, Object> getClipNotes(String trackName, int slotIndex) throws BitwigApiException {
        final String operation = "getClipNotes";
        logger.info("BitwigApiFacade: Reading notes from " + trackName + "[" + slotIndex + "]");

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ClipLauncherSlot slot = getClipLauncherSlot(trackName, slotIndex);

            if (!slot.hasContent().get()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("action", "clip_notes");
                empty.put("track_name", trackName);
                empty.put("slot_index", slotIndex);
                empty.put("has_content", false);
                empty.put("notes", List.of());
                empty.put("note_count", 0);
                return empty;
            }

            slot.select();

            List<Map<String, Object>> notes = new ArrayList<>();
            int maxSteps = 64;  // Reasonable scan range
            int maxKeys = 128;

            for (int step = 0; step < maxSteps; step++) {
                cursorClip.scrollToStep(step);
                for (int key = 0; key < maxKeys; key++) {
                    try {
                        NoteStep noteStep = cursorClip.getStep(0, key, step);
                        if (noteStep != null && noteStep.state() != NoteStep.State.Empty) {
                            Map<String, Object> note = new LinkedHashMap<>();
                            note.put("key", noteStep.y());
                            note.put("step", noteStep.x());
                            note.put("channel", noteStep.channel());
                            note.put("velocity", noteStep.velocity());
                            note.put("duration", noteStep.duration());
                            note.put("state", noteStep.state().name());
                            notes.add(note);

                            if (notes.size() >= 500) break; // safety cap
                        }
                    } catch (Exception e) {
                        // Skip invalid step/key combinations
                    }
                }
                if (notes.size() >= 500) break;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", "clip_notes");
            result.put("track_name", trackName);
            result.put("slot_index", slotIndex);
            result.put("has_content", true);
            result.put("clip_name", slot.name().get());
            result.put("notes", notes);
            result.put("note_count", notes.size());

            logger.info("BitwigApiFacade: Read " + notes.size() + " notes from " + trackName + "[" + slotIndex + "]");
            return result;
        });
    }

    /**
     * Gets clip metadata without reading all notes.
     */
    public Map<String, Object> getClipInfo(String trackName, int slotIndex) throws BitwigApiException {
        final String operation = "getClipInfo";
        logger.info("BitwigApiFacade: Getting clip info for " + trackName + "[" + slotIndex + "]");

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ClipLauncherSlot slot = getClipLauncherSlot(trackName, slotIndex);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", "clip_info");
            result.put("track_name", trackName);
            result.put("slot_index", slotIndex);
            result.put("has_content", slot.hasContent().get());
            result.put("is_playing", slot.isPlaying().get());
            result.put("is_recording", slot.isRecording().get());
            result.put("is_playback_queued", slot.isPlaybackQueued().get());

            if (slot.hasContent().get()) {
                result.put("clip_name", slot.name().get());
                Color color = slot.color().get();
                if (color != null) {
                    result.put("clip_color", String.format("#%02X%02X%02X",
                        (int)(color.getRed() * 255),
                        (int)(color.getGreen() * 255),
                        (int)(color.getBlue() * 255)));
                }

                slot.select();
                if (cursorClip.exists().get()) {
                    result.put("clip_exists", true);
                    try {
                        result.put("play_start", cursorClip.getPlayStart().get());
                        result.put("play_stop", cursorClip.getPlayStop().get());
                    } catch (Exception e) { /* best effort */ }
                }
            }

            return result;
        });
    }

    // ========================================
    // Device & Audio Insertion
    // ========================================

    /**
     * Looks up a device UUID by name (case-insensitive, trims spaces).
     * Accepts both human names ("Polysynth") and raw UUIDs.
     */
    public static String resolveDeviceUuid(String nameOrUuid) {
        if (nameOrUuid == null) return null;
        // If it already looks like a UUID, return as-is
        if (nameOrUuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return nameOrUuid;
        }
        // Case-insensitive lookup
        String key = nameOrUuid.toLowerCase().trim();
        return DEVICE_UUID_MAP.get(key);
    }

    /**
     * Returns the list of known device names that can be inserted by name.
     */
    public static java.util.Set<String> getKnownDeviceNames() {
        return DEVICE_UUID_MAP.keySet();
    }

    /**
     * Inserts a Bitwig built-in device onto a track's device chain.
     */
    public Map<String, Object> addDeviceToTrack(String trackName, String deviceUuid, String position) throws BitwigApiException {
        final String operation = "addDeviceToTrack";
        logger.info("BitwigApiFacade: Adding device " + deviceUuid + " to track '" + trackName + "' at " + position);

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ParameterValidator.validateNotEmpty(trackName, "trackName", operation);
            ParameterValidator.validateNotEmpty(deviceUuid, "deviceUuid", operation);
            final String pos = (position != null && "start".equalsIgnoreCase(position)) ? "start" : "end";

            Optional<Track> trackOpt = findTrackByName(trackName);
            if (trackOpt.isEmpty()) {
                throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                    "Track '" + trackName + "' not found", Map.of("trackName", trackName));
            }

            Track track = trackOpt.get();
            InsertionPoint insertionPoint;
            if ("start".equals(pos)) {
                insertionPoint = track.startOfDeviceChainInsertionPoint();
            } else {
                insertionPoint = track.endOfDeviceChainInsertionPoint();
            }

            insertionPoint.insertBitwigDevice(java.util.UUID.fromString(deviceUuid));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", "device_added");
            result.put("track_name", trackName);
            result.put("device_uuid", deviceUuid);
            result.put("position", pos);
            result.put("message", "Inserted device " + deviceUuid + " at " + pos + " of " + trackName);
            return result;
        });
    }

    /**
     * Inserts an audio file into a clip launcher slot, replacing any existing clip.
     */
    public Map<String, Object> addAudioClipToSlot(String trackName, int slotIndex, String filePath) throws BitwigApiException {
        final String operation = "addAudioClip";
        logger.info("BitwigApiFacade: Adding audio clip from " + filePath + " to " + trackName + "[" + slotIndex + "]");

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ParameterValidator.validateNotEmpty(trackName, "trackName", operation);
            ParameterValidator.validateNotEmpty(filePath, "filePath", operation);

            ClipLauncherSlot slot = getClipLauncherSlot(trackName, slotIndex);

            InsertionPoint insertionPoint = slot.replaceInsertionPoint();
            insertionPoint.insertFile(filePath);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", "audio_clip_added");
            result.put("track_name", trackName);
            result.put("slot_index", slotIndex);
            result.put("file_path", filePath);
            result.put("message", "Inserted audio file into " + trackName + "[" + slotIndex + "]");
            return result;
        });
    }
}
