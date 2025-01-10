package org.jgg;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.glassfish.tyrus.server.Server;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ServerEndpoint("/audio")
public class AudioWebSocketServer {

    private static final int AUDIO_SAMPLE_RATE = 16000; // Sample rate (Hz)
    private static final int CHANNELS = 1; // Mono audio
    private static final int BUFFER_SIZE = 1024; // Buffer size for audio data
    private ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
    private ScheduledExecutorService scheduler;

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("Nueva conexión WebSocket: " + session.getId());
        startAudioSavingTask();
    }

    @OnMessage
    public void onTextMessage(String message, Session session) {
            System.out.println("Mensaje de texto recibido: " + message);
    }

    @OnMessage
    public void onBinaryMessage(ByteBuffer message, Session session){
        System.out.println("Datos binarios recibidos: " + message.toString().length());
        try {
            byte[] audioData = new byte[message.remaining()];
            message.get(audioData);
            synchronized (audioBuffer) {
                audioBuffer.write(audioData);
            }
        } catch (IOException e) {
            System.err.println("Error al guardar datos de audio: " + e.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session) {
        System.out.println("Conexión cerrada: " + session.getId());
        stopAudioSavingTask();
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("Error en sesión " + session.getId() + ": " + throwable.getMessage());
    }

    private void startAudioSavingTask() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            saveAudioToFile();
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void stopAudioSavingTask() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            saveAudioToFile(); // Guardar el audio restante
        }
    }

    private void saveAudioToFile() {
        byte[] audioData;
        synchronized (audioBuffer) {
            audioData = audioBuffer.toByteArray();
            audioBuffer.reset();
        }

        if (audioData.length > 0) {
            String fileName = "audio_" + System.currentTimeMillis() + ".wav";
            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                writeWavHeader(fos, audioData.length);
                fos.write(audioData);
                System.out.println("Archivo de audio guardado: " + fileName);
            } catch (IOException e) {
                System.err.println("Error al guardar archivo WAV: " + e.getMessage());
            }
        }
    }

    private void writeWavHeader(FileOutputStream fos, int audioLength) throws IOException {
        int totalDataLen = audioLength + 36;
        int byteRate = AUDIO_SAMPLE_RATE * CHANNELS * 2;

        fos.write("RIFF".getBytes());
        fos.write(intToBytes(totalDataLen));
        fos.write("WAVE".getBytes());
        fos.write("fmt ".getBytes());
        fos.write(intToBytes(16));
        fos.write(shortToBytes((short) 1)); // Audio format (1 = PCM)
        fos.write(shortToBytes((short) CHANNELS));
        fos.write(intToBytes(AUDIO_SAMPLE_RATE));
        fos.write(intToBytes(byteRate));
        fos.write(shortToBytes((short) (CHANNELS * 2))); // Block align
        fos.write(shortToBytes((short) 16)); // Bits per sample
        fos.write("data".getBytes());
        fos.write(intToBytes(audioLength));
    }

    private byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 24) & 0xff)
        };
    }

    private byte[] shortToBytes(short value) {
        return new byte[]{
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff)
        };
    }

    public static void main(String[] args) {
        Map<String, Object> properties = new HashMap<>();
        //Server server = new Server("localhost", 8080, properties, AudioWebSocketServer.class);
        Server server = new Server("localhost", 3000, "/", properties, AudioWebSocketServer.class);
        try {
            server.start();
            System.out.println("Servidor WebSocket iniciado en ws://localhost:8080/audio");
            System.in.read(); // Mantener el servidor en ejecución
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            server.stop();
        }
    }
}
