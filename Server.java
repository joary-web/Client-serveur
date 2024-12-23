import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {

    private static final int MAIN_SERVER_PORT = 5000;  // Port du serveur principal
    private static final String CONFIG_FILE = "config.txt";  // Fichier de configuration des sous-serveurs
    private static Map<String, List<String>> filePartsMap = new HashMap<>();  // Map pour suivre la répartition des fichiers
    private static List<Integer> subServerPorts = new ArrayList<>();  // Liste des ports des sous-serveurs
    private static ExecutorService executor;  // Pool de threads pour gérer les clients simultanés

    public static void main(String[] args) {
        // Charger les informations du fichier de configuration
        loadConfig();

        // Créer un thread pool pour gérer les clients
        executor = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(MAIN_SERVER_PORT)) {
            System.out.println("Serveur principal démarré sur le port " + MAIN_SERVER_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                

                // Créer un thread pour gérer la connexion client
                executor.submit(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Charger la configuration des sous-serveurs depuis un fichier
    private static void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("subServerPort")) {
                    String[] parts = line.split("=");
                    subServerPorts.add(Integer.parseInt(parts[1].trim()));
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

                String action = input.readUTF();  // Action demandée par le client

                if ("upload".equals(action)) {
                    handleFileUpload(input, output);
                } else if ("download".equals(action)) {
                    handleFileDownload(input, output);
                } else if ("list".equals(action)) {
                    handleFileList(output);
                } else if ("delete".equals(action)) {
                    handleFileDelete(input, output);
                } else {
                    output.writeUTF("Action non reconnue.");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Gérer l'upload d'un fichier
        private void handleFileUpload(DataInputStream input, DataOutputStream output) throws IOException {
            String fileName = input.readUTF();
            long fileSize = input.readLong();
            byte[] fileData = new byte[(int) fileSize];
            input.readFully(fileData);

            // Diviser le fichier en parties et les envoyer aux sous-serveurs
            distributeFile(fileName, fileData);

            output.writeUTF("Fichier uploadé et réparti avec succès.");
        }

        // Répartir le fichier entre les sous-serveurs
        private void distributeFile(String fileName, byte[] fileData) throws IOException {
            int parts = subServerPorts.size();  // Nombre de sous-serveurs
            int partSize = fileData.length / parts;

            for (int i = 0; i < parts; i++) {
                byte[] part = Arrays.copyOfRange(fileData, i * partSize, (i + 1) * partSize);

                try (Socket serverSocket = new Socket("localhost", subServerPorts.get(i));
                     OutputStream out = serverSocket.getOutputStream()) {

                    out.write(part);
                    out.flush();

                    // Sauvegarder l'information de répartition
                    filePartsMap.putIfAbsent(fileName, new ArrayList<>());
                    filePartsMap.get(fileName).add("Part " + (i + 1) + " => Port " + subServerPorts.get(i));
                }
            }
        }

        // Gérer le téléchargement d'un fichier
        private void handleFileDownload(DataInputStream input, DataOutputStream output) throws IOException {
            String fileName = input.readUTF();
            List<String> fileParts = filePartsMap.get(fileName);

            if (fileParts == null) {
                output.writeUTF("Fichier introuvable.");
                return;
            }

            byte[] completeFile = reassembleFile(fileParts);
            output.writeInt(completeFile.length);
            output.write(completeFile);
        }

        // Reconstituer le fichier en récupérant les parties depuis les sous-serveurs
        private byte[] reassembleFile(List<String> fileParts) throws IOException {
            ByteArrayOutputStream completeFileStream = new ByteArrayOutputStream();

            for (String partInfo : fileParts) {
                String[] partDetails = partInfo.split("=>");
                int port = Integer.parseInt(partDetails[1].trim().substring(4));

                try (Socket serverSocket = new Socket("localhost", port);
                     InputStream in = serverSocket.getInputStream()) {

                    byte[] part = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(part)) != -1) {
                        completeFileStream.write(part, 0, bytesRead);
                    }
                }
            }

            return completeFileStream.toByteArray();
        }

        // Gérer la liste des fichiers stockés
        private void handleFileList(DataOutputStream output) throws IOException {
            if (filePartsMap.isEmpty()) {
                output.writeUTF("Aucun fichier trouvé.");
            } else {
                StringBuilder fileList = new StringBuilder();
                for (String fileName : filePartsMap.keySet()) {
                    fileList.append(fileName).append("\n");
                }
                output.writeUTF(fileList.toString());
            }
        }

        // Gérer la suppression d'un fichier
        private void handleFileDelete(DataInputStream input, DataOutputStream output) throws IOException {
            String fileName = input.readUTF();

            List<String> fileParts = filePartsMap.get(fileName);
            if (fileParts == null) {
                output.writeUTF("Fichier introuvable.");
                return;
            }

            // Supprimer les parties du fichier sur chaque sous-serveur
            for (String partInfo : fileParts) {
                String[] partDetails = partInfo.split("=>");
                int port = Integer.parseInt(partDetails[1].trim().substring(4));

                try (Socket serverSocket = new Socket("localhost", port);
                     OutputStream out = serverSocket.getOutputStream()) {

                    out.write("DELETE".getBytes());
                    out.flush();
                }
            }

            // Retirer le fichier de la map
            filePartsMap.remove(fileName);
            output.writeUTF("Fichier supprimé avec succès.");
        }
    }
}
