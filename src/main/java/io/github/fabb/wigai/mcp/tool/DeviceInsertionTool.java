package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tools for inserting Bitwig built-in devices onto tracks.
 * Uses InsertionPoint.insertBitwigDevice(UUID) — no browser popup needed.
 */
public class DeviceInsertionTool {

    // =========================================================
    // add_device_to_track
    // =========================================================

    private static final String ADD_DEVICE_NAME = "add_device_to_track";

    public static McpServerFeatures.SyncToolSpecification addDeviceSpecification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "track_name": {
                  "type": "string",
                  "description": "Name of the track to add the device to (case-sensitive)"
                },
                "device": {
                  "type": "string",
                  "description": "Device name (e.g. 'polysynth', 'compressor', 'filter') or raw UUID. Use list_available_devices to see all known names."
                },
                "position": {
                  "type": "string",
                  "description": "Where to insert: 'end' (default, after existing devices) or 'start' (before existing devices)",
                  "enum": ["end", "start"]
                }
              },
              "required": ["track_name", "device"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(ADD_DEVICE_NAME)
            .description("Insert a Bitwig built-in device onto a track's device chain at start or end position. Use device name like 'polysynth', 'filter', 'compressor', or pass a UUID directly. No browser popup required.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                ADD_DEVICE_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    String trackName = ParameterValidator.validateRequiredString(args, "track_name", ADD_DEVICE_NAME);
                    trackName = ParameterValidator.validateNotEmpty(trackName, "track_name", ADD_DEVICE_NAME);
                    String deviceInput = ParameterValidator.validateRequiredString(args, "device", ADD_DEVICE_NAME);
                    deviceInput = ParameterValidator.validateNotEmpty(deviceInput, "device", ADD_DEVICE_NAME);

                    // Resolve device name to UUID
                    String uuid = BitwigApiFacade.resolveDeviceUuid(deviceInput);
                    if (uuid == null) {
                        throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, ADD_DEVICE_NAME,
                            "Unknown device: '" + deviceInput + "'. Use list_available_devices to see known names, or pass a raw UUID.",
                            Map.of("device", deviceInput));
                    }

                    String position = args.containsKey("position") ? ((String) args.get("position")) : "end";
                    return bitwigApiFacade.addDeviceToTrack(trackName, uuid, position);
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    // =========================================================
    // list_available_devices
    // =========================================================

    private static final String LIST_DEVICES_NAME = "list_available_devices";

    public static McpServerFeatures.SyncToolSpecification listAvailableDevicesSpecification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {},
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name(LIST_DEVICES_NAME)
            .description("List all Bitwig built-in device names that can be inserted via add_device_to_track. Returns device names (case-insensitive for insertion).")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                LIST_DEVICES_NAME,
                logger,
                () -> {
                    var names = BitwigApiFacade.getKnownDeviceNames();
                    return Map.of(
                        "action", "device_list",
                        "device_count", names.size(),
                        "device_names", names
                    );
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
