package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tools for creating and editing clip content in Bitwig Studio.
 * Provides create_clip, delete_clip, add_note, and clear_slot tools.
 */
public class ClipContentTool {

    // =========================================================
    // create_clip
    // =========================================================

    private static final String CREATE_CLIP_NAME = "create_clip";

    public static McpServerFeatures.SyncToolSpecification createClipSpecification(
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
                },
                "length_in_beats": {
                  "type": "integer",
                  "minimum": 1,
                  "description": "Length of the new clip in beats (quarter notes), e.g. 4 for one bar"
                }
              },
              "required": ["track_name", "slot_index", "length_in_beats"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(CREATE_CLIP_NAME)
            .description("Create a new empty MIDI clip in a launcher slot. Overwrites existing clip in that slot.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                CREATE_CLIP_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    String trackName = ParameterValidator.validateRequiredString(args, "track_name", CREATE_CLIP_NAME);
                    trackName = ParameterValidator.validateNotEmpty(trackName, "track_name", CREATE_CLIP_NAME);
                    int slotIndex = ParameterValidator.validateRequiredInteger(args, "slot_index", CREATE_CLIP_NAME);
                    int lengthInBeats = ParameterValidator.validateRequiredInteger(args, "length_in_beats", CREATE_CLIP_NAME);

                    bitwigApiFacade.createClipOnTrack(trackName, slotIndex, lengthInBeats);

                    return Map.of(
                        "action", "clip_created",
                        "track_name", trackName,
                        "slot_index", slotIndex,
                        "length_in_beats", lengthInBeats,
                        "message", "Created empty MIDI clip at " + trackName + "[" + slotIndex + "] (" + lengthInBeats + " beats)"
                    );
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    // =========================================================
    // delete_clip
    // =========================================================

    private static final String DELETE_CLIP_NAME = "delete_clip";

    public static McpServerFeatures.SyncToolSpecification deleteClipSpecification(
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
            .name(DELETE_CLIP_NAME)
            .description("Delete a clip from a launcher slot without deleting the slot itself.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                DELETE_CLIP_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    String trackName = ParameterValidator.validateRequiredString(args, "track_name", DELETE_CLIP_NAME);
                    trackName = ParameterValidator.validateNotEmpty(trackName, "track_name", DELETE_CLIP_NAME);
                    int slotIndex = ParameterValidator.validateRequiredInteger(args, "slot_index", DELETE_CLIP_NAME);

                    bitwigApiFacade.deleteClipOnTrack(trackName, slotIndex);

                    return Map.of(
                        "action", "clip_deleted",
                        "track_name", trackName,
                        "slot_index", slotIndex,
                        "message", "Deleted clip at " + trackName + "[" + slotIndex + "]"
                    );
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    // =========================================================
    // add_note
    // =========================================================

    private static final String ADD_NOTE_NAME = "add_note";

    public static McpServerFeatures.SyncToolSpecification addNoteSpecification(
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
                },
                "key": {
                  "type": "integer",
                  "minimum": 0,
                  "maximum": 127,
                  "description": "MIDI note key (0-127). Middle C = 60, C3 = 48, C5 = 72."
                },
                "step": {
                  "type": "integer",
                  "minimum": 0,
                  "maximum": 127,
                  "description": "Step position in 16th notes (0-127). 0 = first 16th note, 4 = second beat, 16 = bar 2."
                },
                "velocity": {
                  "type": "number",
                  "minimum": 0.0,
                  "maximum": 1.0,
                  "description": "Note velocity (0.0-1.0). 0.0 = silent, 1.0 = full velocity."
                },
                "duration": {
                  "type": "number",
                  "minimum": 0.0,
                  "description": "Note duration in beats (quarter notes). 0.25 = 16th note, 0.5 = 8th note, 1.0 = quarter note, 4.0 = whole note."
                }
              },
              "required": ["track_name", "slot_index", "key", "step", "velocity", "duration"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(ADD_NOTE_NAME)
            .description("Add a MIDI note to a clip. The slot must have a clip (use create_clip first). Selects the slot, then uses CursorClip to place the note at the given key, step, velocity, and duration.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                ADD_NOTE_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    String trackName = ParameterValidator.validateRequiredString(args, "track_name", ADD_NOTE_NAME);
                    trackName = ParameterValidator.validateNotEmpty(trackName, "track_name", ADD_NOTE_NAME);
                    int slotIndex = ParameterValidator.validateRequiredInteger(args, "slot_index", ADD_NOTE_NAME);
                    int key = ParameterValidator.validateRequiredInteger(args, "key", ADD_NOTE_NAME);
                    int step = ParameterValidator.validateRequiredInteger(args, "step", ADD_NOTE_NAME);

                    Object velObj = args.get("velocity");
                    double velocity = velObj instanceof Number ? ((Number) velObj).doubleValue() : 1.0;
                    Object durObj = args.get("duration");
                    double duration = durObj instanceof Number ? ((Number) durObj).doubleValue() : 0.25;

                    bitwigApiFacade.addNoteToClip(trackName, slotIndex, key, step, velocity, duration);

                    return Map.of(
                        "action", "note_added",
                        "track_name", trackName,
                        "slot_index", slotIndex,
                        "key", key,
                        "step", step,
                        "velocity", velocity,
                        "duration", duration,
                        "message", "Added note key=" + key + " step=" + step + " vel=" + velocity + " dur=" + duration + " to " + trackName + "[" + slotIndex + "]"
                    );
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    // =========================================================
    // clear_slot
    // =========================================================

    private static final String CLEAR_SLOT_NAME = "clear_slot";

    public static McpServerFeatures.SyncToolSpecification clearSlotSpecification(
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
            .name(CLEAR_SLOT_NAME)
            .description("Clear all notes/steps from a clip without deleting the clip itself. The slot must have a clip.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                CLEAR_SLOT_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    String trackName = ParameterValidator.validateRequiredString(args, "track_name", CLEAR_SLOT_NAME);
                    trackName = ParameterValidator.validateNotEmpty(trackName, "track_name", CLEAR_SLOT_NAME);
                    int slotIndex = ParameterValidator.validateRequiredInteger(args, "slot_index", CLEAR_SLOT_NAME);

                    bitwigApiFacade.clearSlotOnTrack(trackName, slotIndex);

                    return Map.of(
                        "action", "slot_cleared",
                        "track_name", trackName,
                        "slot_index", slotIndex,
                        "message", "Cleared all notes from " + trackName + "[" + slotIndex + "]"
                    );
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
