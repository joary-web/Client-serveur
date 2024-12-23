
import java.io.*;
import java.net.*;
import java.util.*;

public class M3 {

    private static final int PORT = 5003;  // Chaque sous-serveur aura un port distinct (5001, 5002, etc.)
    private static Map<String, List<byte[]>> fileStore = new HashMap<>();  // Carte pour stocker les fichiers

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Sous-serveur démarré sur le port " + PORT);

            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Connexion acceptée depuis : " + clientSocket.getInetAddress());

                    // Gérer la requête du client dans un thread
                    new Thread(new ClientHandler(clientSocket)).start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Classe pour gérer les requêtes des clients
    private static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (DataInputStream input = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream())) {

                String action = input.readUTF();

                if ("upload".equals(action)) {
                    handleUpload(input, output);
                } else if ("download".equals(action)) {
                    handleDownload(input, output);
                } else if ("delete".equals(action)) {
                    handleDelete(input, output);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Gérer l'upload d'un fichier
        private void handleUpload(DataInputStream input, DataOutputStream output) throws IOException {
            String fileName = input.readUTF();
            long fileSize = input.readLong();
            byte[] fileData = new byte[(int) fileSize];
            input.readFully(fileData);

            // Stocker le fichier dans le sous-serveur
            fileStore.put(fileName, new ArrayList<>(Collections.singletonList(fileData)));

            output.writeUTF("Fichier stocké avec succès.");
        }

        // Gérer le téléchargement d'un fichier
        private void handleDownload(DataInputStream input, DataOutputStream output) throws IOException {
            String fileName = input.readUTF();

            List<byte[]> fileData = fileStore.get(fileName);
            if (fileData == null || fileData.isEmpty()) {
                output.writeUTF("Fichier introuvable.");
                return;
            }

            byte[] fileBytes = fileData.get(0);
            output.writeInt(fileBytes.length);
            output.write(fileBytes);
        }

        // Gérer la suppression d'un fichier
        private void handleDelete(DataInputStream input, DataOutputStream output) throws IOException {
            String fileName = input.readUTF();

            if (fileStore.containsKey(fileName)) {
                fileStore.remove(fileName);
                output.writeUTF("Fichier supprimé.");
            } else {
                output.writeUTF("Fichier introuvable.");
            }
        }
    }
}