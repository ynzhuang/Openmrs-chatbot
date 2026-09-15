<%@ include file="/WEB-INF/template/include.jsp"%>

<%@ include file="/WEB-INF/template/header.jsp"%>

<style>
.chat-container {
    max-width: 800px;
    margin: 20px auto;
    background: #fff;
    border-radius: 10px;
    box-shadow: 0 4px 20px rgba(0,0,0,0.1);
    overflow: hidden;
}

.chat-header {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    padding: 20px;
    text-align: center;
}

.chat-header h2 {
    margin: 0;
    font-size: 24px;
    font-weight: 600;
}

.chat-status {
    background: #fff3cd;
    border: 1px solid #ffeaa7;
    color: #856404;
    padding: 15px;
    margin: 20px;
    border-radius: 8px;
    text-align: center;
    font-weight: 500;
}

.chat-config {
    margin: 0 20px 10px 20px;
    display: flex;
    gap: 10px;
    align-items: center;
}

.chat-config input {
    flex: 1;
    padding: 8px 12px;
    border: 1px solid #e1e4e8;
    border-radius: 6px;
}

.chat-messages {
    height: 400px;
    overflow-y: auto;
    padding: 20px;
    background: #f8f9fa;
}

.message {
    margin-bottom: 15px;
    display: flex;
    align-items: flex-start;
}

.message.bot {
    justify-content: flex-start;
}

.message.user {
    justify-content: flex-end;
}

.message-content {
    max-width: 70%;
    padding: 12px 16px;
    border-radius: 18px;
    word-wrap: break-word;
}

.message.bot .message-content {
    background: #e3f2fd;
    color: #1565c0;
    border-bottom-left-radius: 4px;
}

.message.user .message-content {
    background: #667eea;
    color: white;
    border-bottom-right-radius: 4px;
}

.chat-input {
    padding: 20px;
    background: white;
    border-top: 1px solid #e9ecef;
    display: flex;
    gap: 10px;
}

.chat-input input {
    flex: 1;
    padding: 12px 16px;
    border: 2px solid #e9ecef;
    border-radius: 25px;
    outline: none;
    font-size: 14px;
    transition: border-color 0.3s;
}

.chat-input input:focus {
    border-color: #667eea;
}

.chat-input button {
    background: #667eea;
    color: white;
    border: none;
    padding: 12px 24px;
    border-radius: 25px;
    cursor: pointer;
    font-weight: 500;
    transition: background 0.3s;
}

.chat-input button:hover {
    background: #5a6fd8;
}

.chat-input button:disabled {
    background: #ccc;
    cursor: not-allowed;
}

.debug-box {
    margin: 10px 20px 20px 20px;
    background: #f1f3f5;
    border: 1px solid #e9ecef;
    border-radius: 8px;
    padding: 12px;
    font-size: 12px;
    color: #495057;
    display: none;
}
</style>

<div class="chat-container">
    <div class="chat-header">
        <h2><spring:message code="chatbot.title" /></h2>
    </div>
    
    <div class="chat-status">
        🟢 Chatbot is ready. Provide a patient ID (identifier) or UUID.
    </div>

    <div class="chat-config">
        <label for="patientUuidInput">Patient ID/UUID</label>
        <input type="text" id="patientUuidInput" placeholder="e.g. 100GEJ, or 6f1f23a0-..." />
        <button onclick="toggleDebug()" id="debugToggle">Debug</button>
    </div>
    
    <div class="chat-messages" id="chatMessages">
        <div class="message bot">
            <div class="message-content">
                Hello! Ask your question (e.g., "What medications is this patient currently on?").
            </div>
        </div>
    </div>
    
    <div class="chat-input">
        <input type="text" id="messageInput" placeholder="Type your message here..." onkeypress="handleKeyPress(event)">
        <button onclick="sendMessage()" id="sendButton">Send</button>
    </div>

    <div class="debug-box" id="debugBox">
        <div><strong>Routing/Execution Debug</strong></div>
        <pre id="debugPre" style="white-space: pre-wrap; margin: 0;"></pre>
    </div>
</div>

<script>
const ANSWER_URL = '<c:url value="/module/chatbot/answer.form"/>';
let debugOpen = false;

function isUuidLike(value) {
    if (!value) return false;
    const v = value.trim();
    return v.length >= 32 && v.indexOf('-') >= 0;
}

function sendMessage() {
    const input = document.getElementById('messageInput');
    const message = input.value.trim();
    const patientInput = document.getElementById('patientUuidInput').value.trim();

    if (!message) return;

    addMessage(message, 'user');
    input.value = '';

    const sendBtn = document.getElementById('sendButton');
    sendBtn.disabled = true;
    addMessage('Thinking…', 'bot');

    const context = {};
    if (patientInput) {
        if (isUuidLike(patientInput)) {
            context.patient = { uuid: patientInput };
        } else {
            context.patient = { identifier: patientInput };
        }
    }

    const payload = {
        question: message,
        context: context,
        max: 2
    };
    fetch(ANSWER_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(payload)
    }).then(async (r) => {
          const text = await r.text();
          try { return JSON.parse(text); } catch (e) { throw new Error(text.substring(0,200)); }
      })
      .then(data => {
          removeLastBotThinking();
          if (data.error) {
              addMessage('Error: ' + data.error, 'bot');
          } else {
              if (data.answer) {
                  addMessage(data.answer, 'bot');
              } else {
                  addMessage('No answer generated. Please refine your question.', 'bot');
              }
              const dbg = {
                  chosen: data.chosen,
                  resultsCount: (data.results || []).length,
              };
              setDebug(JSON.stringify(dbg, null, 2));
          }
      })
      .catch(err => {
          removeLastBotThinking();
          addMessage('Request failed: ' + err, 'bot');
      })
      .finally(() => { sendBtn.disabled = false; });
}

function addMessage(text, sender) {
    const chatMessages = document.getElementById('chatMessages');
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${sender}`;
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    contentDiv.textContent = text;
    messageDiv.appendChild(contentDiv);
    chatMessages.appendChild(messageDiv);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function removeLastBotThinking() {
    const chatMessages = document.getElementById('chatMessages');
    const nodes = chatMessages.querySelectorAll('.message.bot .message-content');
    for (let i = nodes.length - 1; i >= 0; i--) {
        if (nodes[i].textContent === 'Thinking…') {
            nodes[i].parentElement.parentElement.remove();
            break;
        }
    }
}

function handleKeyPress(event) {
    if (event.key === 'Enter') {
        sendMessage();
    }
}

function toggleDebug() {
    debugOpen = !debugOpen;
    document.getElementById('debugBox').style.display = debugOpen ? 'block' : 'none';
}

function setDebug(text) {
    document.getElementById('debugPre').textContent = text || '';
}

// Focus on input when page loads
document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('messageInput').focus();
});
</script>

<%@ include file="/WEB-INF/template/footer.jsp"%>
