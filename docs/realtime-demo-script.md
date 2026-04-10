# Realtime Demo Talk Track

1. Start the stack with `scripts/realtime-demo.sh` and open `http://127.0.0.1:5173`.
2. Explain the topology: Coral server orchestrates four local executable agents (sanctions, PEP, adverse media, risk scoring).
3. Show the newly created session ID printed by the script and call out that the React app discovers sessions from `/api/v1/local/namespace/extended`.
4. Highlight live behavior: the frontend subscribes to `ws/v1/events/{token}/session/{namespace}/{sessionId}` and updates agent status and message flow in real time.
5. Walk the agent sequence: sanctions/PEP/adverse agents produce findings, then the risk-scoring agent aggregates outputs into a final decision.
6. If asked for observability, point to the script log directory and the server websocket logs endpoint `ws/v1/logs/{token}`.
7. End by switching `--subject` to a second entity and rerunning to demonstrate repeatability.
