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
 * MCP tools for creating audio clips from files in clip launcher slots.
 * Uses ClipLauncherSlot.replaceInsertionPoint().insertFile().
 */
public class AudioClipTool {

    // =========================================================
    // create_audio_clip
    // =========================================================

    private static final String CREATE_AUDIO_CLIP_NAME = "create_audio_clip";

    public static McpServerFeatures.SyncToolSpecification createAudioClipSpecification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "track_name": {
                  "type": "string",
                  "description": "Name of the track (case-sensitive). Use an audio track for audio clips."
                },
                "slot_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based clip launcher slot index (scene position)"
                },
                "file_path": {
                  "type": "string",
                  "description": "Absolute path to the audio file (WAV, MP3, AIFF, etc.)"
                }
              },
              "required": ["track_name", "slot_index", "file_path"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(CREATE_AUDIO_CLIP_NAME)
            .description("Insert an audio file into a clip launcher slot, replacing any existing clip. Supports WAV, MP3, AIFF, FLAC, and other formats Bitwig can import.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                CREATE_AUDIO_CLIP_NAME,
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    String trackName = ParameterValidator.validateRequiredString(args, "track_name", CREATE_AUDIO_CLIP_NAME);
                    trackName = ParameterValidator.validateNotEmpty(trackName, "track_name", CREATE_AUDIO_CLIP_NAME);
                    int slotIndex = ParameterValidator.validateRequiredInteger(args, "slot_index", CREATE_AUDIO_CLIP_NAME);
                    String filePath = ParameterValidator.validateRequiredString(args, "file_path", CREATE_AUDIO_CLIP_NAME);
                    filePath = ParameterValidator.validateNotEmpty(filePath, "file_path", CREATE_AUDIO_CLIP_NAME);

                    return bitwigApiFacade.addAudioClipToSlot(trackName, slotIndex, filePath);
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
