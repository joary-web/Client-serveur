
import java.io.*;
import java.net.*;
import java.util.*;

public class Clients {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;  // Port du serveur principal
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("Connecté au serveur principal.");

            while (true) {
                System.out.println("Choisissez une action:");
                System.out.println("1. Télécharger un fichier");
                System.out.println("2. Uploader un fichier");
                System.out.println("3. Lister les fichiers");
                System.out.println("4. Supprimer un fichier");
                System.out.println("5. Quitter");
                System.out.print("Votre choix: ");
                int choice = scanner.nextInt();
                scanner.nextLine();  // Consume newline

                switch (choice) {
                    case 1:
                        handleDownload(input, output);
                        break;
                    case 2:
                        handleUpload(input, output);
                        break;
                    case 3:
                        handleListFiles(input, output);
                        break;
                    case 4:
                        handleDeleteFile(input, output);
                        break;
                    case 5:
                        System.out.println("Au revoir!");
                        return;
                    default:
                        System.out.println("Choix invalide.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Télécharger un fichier
    private static void handleDownload(DataInputStream input, DataOutputStream output) throws IOException {
        System.out.print("Nom du fichier à télécharger : ");
        String fileName = scanner.nextLine();
        output.writeUTF("download");
        output.writeUTF(fileName);

        int fileSize = input.readInt();
        if (fileSize == -1) {
            System.out.println("Fichier introuvable.");
        } else {
            byte[] fileData = new byte[fileSize];
            input.readFully(fileData);

            try (FileOutputStream fileOut = new FileOutputStream("downloaded_" + fileName)) {
                fileOut.write(fileData);
                System.out.println("Fichier téléchargé avec succès.");
            }
        }
    }

    // Uploader un fichier
    private static void handleUpload(DataInputStream input, DataOutputStream output) throws IOException {
        System.out.print("Nom du fichier à uploader : ");
        String fileName = scanner.nextLine();
        File file = new File(fileName);

        if (!file.exists()) {
            System.out.println("Le fichier n'existe pas.");
            return;
        }

        output.writeUTF("upload");
        output.writeUTF(fileName);
        output.writeLong(file.length());

        try (FileInputStream fileIn = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            System.out.println("Fichier envoyé avec succès.");
        }
    }

    // Lister les fichiers disponibles
    private static void handleListFiles(DataInputStream input, DataOutputStream output) throws IOException {
        output.writeUTF("list");
        String fileList = input.readUTF();
        System.out.println("Fichiers disponibles :");
        System.out.println(fileList);
    }

    // Supprimer un fichier
    private static void handleDeleteFile(DataInputStream input, DataOutputStream output) throws IOException {
        System.out.print("Nom du fichier à supprimer : ");
        String fileName = scanner.nextLine();

        output.writeUTF("delete");
        output.writeUTF(fileName);

        String response = input.readUTF();
        System.out.println(response);
    }
}