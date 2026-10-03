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

        // 3. Dynamic Model Discovery & Fallback List
        let candidateModels = [
            'gemini-1.5-flash',
            'gemini-1.5-flash-latest',
            'gemini-1.5-flash-8b',
            'gemini-2.0-flash-exp',
            'gemini-1.5-pro'
        ];

        try {
            const modelsListUrl = `https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey}`;
            const listRes = await fetch(modelsListUrl);
            if (listRes.ok) {
                const listData = await listRes.json();
                if (Array.isArray(listData.models)) {
                    const available = listData.models
                        .filter(m => Array.isArray(m.supportedGenerationMethods) && m.supportedGenerationMethods.includes('generateContent'))
                        .map(m => m.name.replace(/^models\//, ''));

                    if (available.length > 0) {
                        const flashModels = available.filter(m => m.includes('flash'));
                        const proModels = available.filter(m => m.includes('pro') && !m.includes('flash'));
                        const otherModels = available.filter(m => !m.includes('flash') && !m.includes('pro'));

                        candidateModels = [...flashModels, ...proModels, ...otherModels];
                        console.log(`ℹ️ Discovered ${available.length} active Gemini models. Selected primary model: ${candidateModels[0]}`);
                    }
                }
            }
        } catch (listErr) {
            console.warn("⚠️ Could not fetch models list dynamically, using default candidate list.", listErr.message);
        }

        let lastData = null;
        let lastStatus = 500;
        let textReply = null;

        for (const model of candidateModels) {
            const modelName = model.startsWith('models/') ? model : `models/${model}`;
            const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/${modelName}:generateContent?key=${apiKey}`;

            const systemInstruction = "You are AgriFlow Farm Assistant, an expert agricultural AI assistant. Help farmers understand their telemetry, irrigation, schedules, and alerts. Keep answers clear, friendly, and well-structured.";

            const userContent = context
                ? `${systemInstruction}\n\nLive Farm Context:\n${context}\n\nUser Question:\n${userQuery.trim()}`
                : `${systemInstruction}\n\nUser Question:\n${userQuery.trim()}`;

            const payload = {
                contents: [
                    {
                        role: "user",
                        parts: [{ text: userContent }]
                    }
                ],
                generationConfig: {
                    temperature: 0.7,
                    maxOutputTokens: 1000
                }
            };

            try {
                const response = await fetch(geminiUrl, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(payload)
                });

                lastStatus = response.status;
                lastData = await response.json();

                if (response.ok) {
                    const candidates = lastData?.candidates;
                    textReply = candidates?.[0]?.content?.parts?.[0]?.text;
                    if (textReply) {
                        console.log(`✅ Successfully generated response using model: ${modelName}`);
                        break;
                    }
                } else {
                    console.error(`❌ Gemini API Error for model ${modelName} (${response.status}):`, JSON.stringify(lastData));
                }
            } catch (fetchErr) {
                console.error(`❌ Network error contacting model ${modelName}:`, fetchErr);
            }
        }

        // 4. Handle Failure if no model generated a response
        if (!textReply) {
            const errorDetails = lastData?.error?.message || "Unable to generate response from Gemini API.";

            if (lastStatus === 429) {
                return res.status(429).json({
                    error: "Gemini API rate limit exceeded.",
                    reply: "Rate limit reached for the Gemini Free Tier. Please wait a moment and try again."
                });
            }

            return res.status(lastStatus || 500).json({
                error: errorDetails,
                reply: `Gemini API Error: ${errorDetails}`
            });
        }

        // 5. Return in required JSON format: { "reply": "AI response here" }
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
