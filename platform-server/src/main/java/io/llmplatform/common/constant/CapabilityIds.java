package io.llmplatform.common.constant;

/** 平台可选能力标识。 */
public final class CapabilityIds {

    public static final String CHAT = "ai.chat";
    public static final String CONTEXT = "conversation.context";
    public static final String RAG = "rag";
    public static final String TOOLS = "tools";
    public static final String MCP = "mcp";
    public static final String SKILLS = "skills";
    public static final String NETWORK = "network";
    public static final String EMBEDDING = "embedding";
    public static final String GRAPH_RAG = "graph.rag";
    public static final String HYDE = "hyde";

    private CapabilityIds() {}

    public static String[] all() {
        return new String[] {
            CHAT, CONTEXT, RAG, TOOLS, MCP, SKILLS, NETWORK, EMBEDDING, GRAPH_RAG, HYDE
        };
    }
}
