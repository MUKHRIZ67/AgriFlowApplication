const express = require('express');
const cors = require('cors');
const dotenv = require('dotenv');
const path = require('path');

// Load environment variables from .env in server/ or root directory
dotenv.config();
dotenv.config({ path: path.join(__dirname, '..', '.env') });

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// Health check endpoint
app.get('/', (req, res) => {
    res.json({
        status: 'online',
        service: 'AgriFlow Chatbot Backend (Google Gemini API)',
        endpoint: 'POST /api/chat'
    });
});

app.get('/api/health', (req, res) => {
    const hasApiKey = Boolean(process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY !== 'your_google_ai_studio_api_key_here');
    res.json({
        status: 'ok',
        apiKeyConfigured: hasApiKey
    });
});

// Chat endpoint - POST /api/chat
app.post('/api/chat', async (req, res) => {
    try {
        const { message, prompt, context } = req.body || {};
        const userQuery = message || prompt;

        // 1. Input Validation
        if (!userQuery || typeof userQuery !== 'string' || !userQuery.trim()) {
            return res.status(400).json({
                error: "Invalid request: 'message' or 'prompt' field is required.",
                reply: "Error: Please enter a valid message."
            });
        }

        // 2. API Key Check
        const apiKey = process.env.GEMINI_API_KEY;
        if (!apiKey || apiKey === 'your_google_ai_studio_api_key_here') {
            console.error("❌ GEMINI_API_KEY is missing or unconfigured.");
            return res.status(500).json({
                error: "GEMINI_API_KEY environment variable is not configured on the backend server.",
                reply: "Error: GEMINI_API_KEY environment variable is not set on the server. Please add your Google AI Studio API key to .env file."
            });
        }

        // 3. Prepare Gemini API Request
        // Using Free Tier eligible model: gemini-1.5-flash
        const model = 'gemini-1.5-flash';
        const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;

        const systemPrompt = "You are AgriFlow Farm Assistant, an expert agricultural AI assistant. You help farmers understand their live sensor telemetry, irrigation conditions, fertilizer schedules, and alerts. Provide clear, accurate, friendly, and well-structured answers.";

        const userContent = context
            ? `Farm Live Context:\n${context}\n\nUser Question:\n${userQuery.trim()}`
            : userQuery.trim();

        const payload = {
            contents: [
                {
                    role: "user",
                    parts: [{ text: userContent }]
                }
            ],
            systemInstruction: {
                parts: [{ text: systemPrompt }]
            },
            generationConfig: {
                temperature: 0.7,
                maxOutputTokens: 1000
            }
        };

        // 4. Call Google Gemini API
        const response = await fetch(geminiUrl, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });

        const data = await response.json();

        // 5. Handle Gemini API Error Responses
        if (!response.ok) {
            console.error(`❌ Gemini API Error (${response.status}):`, JSON.stringify(data));

            // Rate Limit / Quota Exceeded Error (HTTP 429)
            if (response.status === 429) {
                return res.status(429).json({
                    error: "Gemini API rate limit exceeded.",
                    reply: "Rate limit reached for the Gemini Free Tier. Please wait a moment and try again."
                });
            }

            // Unauthorized / Invalid API Key (HTTP 400 or 403)
            if (response.status === 400 || response.status === 401 || response.status === 403) {
                const errorMsg = data?.error?.message || "Invalid or unauthorized API Key.";
                return res.status(response.status).json({
                    error: errorMsg,
                    reply: `Gemini API Key Error: ${errorMsg}`
                });
            }

            // Other Gemini Failures
            return res.status(response.status || 500).json({
                error: data?.error?.message || "Gemini API service error.",
                reply: "Sorry, I encountered an issue contacting the Gemini AI service. Please try again later."
            });
        }

        // 6. Extract Response Text
        const candidates = data?.candidates;
        const textReply = candidates?.[0]?.content?.parts?.[0]?.text;

        if (!textReply) {
            return res.json({
                reply: "I received your request, but no response text was generated. Please try asking in a different way."
            });
        }

        // 7. Return in required JSON format: { "reply": "AI response here" }
        return res.json({
            reply: textReply.trim()
        });

    } catch (err) {
        console.error("❌ Backend Server Error:", err);
        return res.status(500).json({
            error: "Internal server error occurred.",
            reply: "Server error occurred while communicating with Gemini API. Please check backend logs."
        });
    }
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`==================================================`);
    console.log(`🚀 AgriFlow Chatbot Backend Server Running!`);
    console.log(`📍 URL: http://localhost:${PORT}`);
    console.log(`💬 Chat Endpoint: POST http://localhost:${PORT}/api/chat`);
    console.log(`🔑 GEMINI_API_KEY Configured: ${process.env.GEMINI_API_KEY ? 'YES' : 'NO (Missing!)'}`);
    console.log(`==================================================`);
});
