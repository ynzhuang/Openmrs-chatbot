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

.development-info {
    background: #e8f5e8;
    border: 1px solid #c3e6c3;
    color: #2d5a2d;
    padding: 15px;
    margin: 20px;
    border-radius: 8px;
    text-align: center;
}

.development-info h4 {
    margin: 0 0 10px 0;
    color: #1e7e34;
}

.development-info p {
    margin: 5px 0;
    font-size: 14px;
}
</style>

<div class="chat-container">
    <div class="chat-header">
        <h2><spring:message code="chatbot.title" /></h2>
    </div>
    
    <div class="chat-status">
        🟢 Chatbot is running and ready to assist you
    </div>
    
    <div class="development-info">
        <h4>🚧 Development Phase</h4>
        <p>This chatbot is currently in development</p>
        <p>Features coming soon: AI responses, patient data integration, appointment scheduling</p>
    </div>
    
    <div class="chat-messages" id="chatMessages">
        <div class="message bot">
            <div class="message-content">
                Hello! I'm your OpenMRS Chatbot Assistant. How can I help you today?
            </div>
        </div>
        
        <div class="message bot">
            <div class="message-content">
                I can help you with patient information, appointments, and general inquiries. What would you like to know?
            </div>
        </div>
    </div>
    
    <div class="chat-input">
        <input type="text" id="messageInput" placeholder="Type your message here..." onkeypress="handleKeyPress(event)">
        <button onclick="sendMessage()" id="sendButton">Send</button>
    </div>
</div>

<script>
function sendMessage() {
    const input = document.getElementById('messageInput');
    const message = input.value.trim();
    
    if (message) {
        addMessage(message, 'user');
        input.value = '';
        
        // Simulate bot response (in development phase)
        setTimeout(() => {
            addMessage('Thank you for your message! This feature is currently in development. I\'ll be able to provide more intelligent responses soon.', 'bot');
        }, 1000);
    }
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
    
    // Scroll to bottom
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function handleKeyPress(event) {
    if (event.key === 'Enter') {
        sendMessage();
    }
}

// Focus on input when page loads
document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('messageInput').focus();
});
</script>

<%@ include file="/WEB-INF/template/footer.jsp"%>
