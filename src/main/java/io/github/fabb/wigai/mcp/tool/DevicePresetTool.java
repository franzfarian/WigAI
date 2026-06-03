package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tools for device selection, preset browsing, and preset/page loading.
 * Enables an AI agent to autonomously pick sounds and navigate device parameters.
 */
public class DevicePresetTool {

    // =========================================================
    // select_device
    // =========================================================

    private static final String SELECT_DEVICE_NAME = "select_device";

    public static McpServerFeatures.SyncToolSpecification selectDeviceSpecification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "track_name": {
                  "type": "string",
                  "description": "Name of the track (case-sensitive)"
                },
                "device_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based index of the device in the track's device chain"
                }
              },
              "required": ["track_name", "device_index"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(SELECT_DEVICE_NAME)
            .description("Select a device on a track by index, making it the cursor device for parameter/preset control.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                SELECT_DEVICE_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    String trackName = ParameterValidator.validateRequiredString(args, "track_name", SELECT_DEVICE_NAME);
                    trackName = ParameterValidator.validateNotEmpty(trackName, "track_name", SELECT_DEVICE_NAME);
                    int deviceIndex = ParameterValidator.validateRequiredInteger(args, "device_index", SELECT_DEVICE_NAME);

                    return bitwigApiFacade.selectDeviceOnTrack(trackName, deviceIndex);
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    // =========================================================
    // get_device_presets
    // =========================================================

    private static final String GET_DEVICE_PRESETS_NAME = "get_device_presets";

    public static McpServerFeatures.SyncToolSpecification getDevicePresetsSpecification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {},
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name(GET_DEVICE_PRESETS_NAME)
            .description("Get all preset names, categories, creators, and page names for the currently selected device. Returns cached data from registered observers.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                GET_DEVICE_PRESETS_NAME,
                logger,
                () -> bitwigApiFacade.getDevicePresets()
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    // =========================================================
    // select_device_preset
    // =========================================================

    private static final String SELECT_DEVICE_PRESET_NAME = "select_device_preset";

    public static McpServerFeatures.SyncToolSpecification selectDevicePresetSpecification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "preset_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based preset index to load. Use get_device_presets first to see available presets."
                }
              },
              "required": ["preset_index"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(SELECT_DEVICE_PRESET_NAME)
            .description("Load a preset by index on the currently selected device. Use get_device_presets to list available presets first.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                SELECT_DEVICE_PRESET_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    int presetIndex = ParameterValidator.validateRequiredInteger(args, "preset_index", SELECT_DEVICE_PRESET_NAME);
                    return bitwigApiFacade.loadPreset(presetIndex);
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    // =========================================================
    // navigate_device_presets
    // =========================================================

    private static final String NAVIGATE_DEVICE_PRESETS_NAME = "navigate_device_presets";

    public static McpServerFeatures.SyncToolSpecification navigateDevicePresetsSpecification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "direction": {
                  "type": "string",
                  "description": "Direction to navigate: 'next' or 'previous'",
                  "enum": ["next", "previous"]
                }
              },
              "required": ["direction"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(NAVIGATE_DEVICE_PRESETS_NAME)
            .description("Navigate to the next or previous preset on the currently selected device. Returns the new preset name and category.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                NAVIGATE_DEVICE_PRESETS_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    String direction = ParameterValidator.validateRequiredString(args, "direction", NAVIGATE_DEVICE_PRESETS_NAME);
                    return bitwigApiFacade.navigatePreset(direction);
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    // =========================================================
    // navigate_device_preset_categories
    // =========================================================

    private static final String NAVIGATE_PRESET_CATEGORIES_NAME = "navigate_device_preset_categories";

    public static McpServerFeatures.SyncToolSpecification navigatePresetCategoriesSpecification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "direction": {
                  "type": "string",
                  "description": "Direction to navigate: 'next' or 'previous'",
                  "enum": ["next", "previous"]
                }
              },
              "required": ["direction"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(NAVIGATE_PRESET_CATEGORIES_NAME)
            .description("Navigate to the next or previous preset category on the currently selected device.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                NAVIGATE_PRESET_CATEGORIES_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    String direction = ParameterValidator.validateRequiredString(args, "direction", NAVIGATE_PRESET_CATEGORIES_NAME);
                    return bitwigApiFacade.navigatePresetCategory(direction);
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    // =========================================================
    // select_device_page
    // =========================================================

    private static final String SELECT_DEVICE_PAGE_NAME = "select_device_page";

    public static McpServerFeatures.SyncToolSpecification selectDevicePageSpecification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "page_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based parameter page index to select. Use get_device_presets to see available page names."
                }
              },
              "required": ["page_index"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(SELECT_DEVICE_PAGE_NAME)
            .description("Select a parameter page by index on the currently selected device. Pages expose different sets of 8 remote controls.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                SELECT_DEVICE_PAGE_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    int pageIndex = ParameterValidator.validateRequiredInteger(args, "page_index", SELECT_DEVICE_PAGE_NAME);
                    return bitwigApiFacade.selectDevicePage(pageIndex);
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
