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
 * MCP tools for reading MIDI clip content, clip metadata, and clip selection.
 * Enables an AI agent to inspect and understand what's in clips.
 */
public class ClipNoteTool {

    // =========================================================
    // get_clip_notes
    // =========================================================

    private static final String GET_CLIP_NOTES_NAME = "get_clip_notes";

    public static McpServerFeatures.SyncToolSpecification getClipNotesSpecification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "track_name": {
                  "type": "string",
                  "description": "Name of the track (case-sensitive)"
                },
                "slot_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based clip launcher slot index (scene position)"
                }
              },
              "required": ["track_name", "slot_index"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(GET_CLIP_NOTES_NAME)
            .description("Read all MIDI notes from a clip. Returns key, step, velocity, duration, and state for each note (up to 500).")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                GET_CLIP_NOTES_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    String trackName = ParameterValidator.validateRequiredString(args, "track_name", GET_CLIP_NOTES_NAME);
                    trackName = ParameterValidator.validateNotEmpty(trackName, "track_name", GET_CLIP_NOTES_NAME);
                    int slotIndex = ParameterValidator.validateRequiredInteger(args, "slot_index", GET_CLIP_NOTES_NAME);
                    return bitwigApiFacade.getClipNotes(trackName, slotIndex);
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    // =========================================================
    // get_clip_info
    // =========================================================

    private static final String GET_CLIP_INFO_NAME = "get_clip_info";

    public static McpServerFeatures.SyncToolSpecification getClipInfoSpecification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "track_name": {
                  "type": "string",
                  "description": "Name of the track (case-sensitive)"
                },
                "slot_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based clip launcher slot index (scene position)"
                }
              },
              "required": ["track_name", "slot_index"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(GET_CLIP_INFO_NAME)
            .description("Get clip metadata: name, color, playback state, play start/stop. Does not read notes (use get_clip_notes for that).")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                GET_CLIP_INFO_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    String trackName = ParameterValidator.validateRequiredString(args, "track_name", GET_CLIP_INFO_NAME);
                    trackName = ParameterValidator.validateNotEmpty(trackName, "track_name", GET_CLIP_INFO_NAME);
                    int slotIndex = ParameterValidator.validateRequiredInteger(args, "slot_index", GET_CLIP_INFO_NAME);
                    return bitwigApiFacade.getClipInfo(trackName, slotIndex);
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    // =========================================================
    // select_clip
    // =========================================================

    private static final String SELECT_CLIP_NAME = "select_clip";

    public static McpServerFeatures.SyncToolSpecification selectClipSpecification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "track_name": {
                  "type": "string",
                  "description": "Name of the track (case-sensitive)"
                },
                "slot_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based clip launcher slot index (scene position)"
                }
              },
              "required": ["track_name", "slot_index"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(SELECT_CLIP_NAME)
            .description("Select a clip launcher slot, making it the active cursor clip for subsequent note operations (add_note, get_clip_notes, clear_slot).")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                SELECT_CLIP_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    String trackName = ParameterValidator.validateRequiredString(args, "track_name", SELECT_CLIP_NAME);
                    trackName = ParameterValidator.validateNotEmpty(trackName, "track_name", SELECT_CLIP_NAME);
                    int slotIndex = ParameterValidator.validateRequiredInteger(args, "slot_index", SELECT_CLIP_NAME);
                    return bitwigApiFacade.selectClipSlot(trackName, slotIndex);
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
