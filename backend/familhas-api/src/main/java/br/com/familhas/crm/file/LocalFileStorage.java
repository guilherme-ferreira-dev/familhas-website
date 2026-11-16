package br.com.familhas.crm.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * Volume local privado. A chave precisa ser um UUID: qualquer outra coisa (barra, "..", nome do usuário) é recusada
 * antes de tocar no disco, e o caminho final ainda é conferido contra a raiz (path traversal).
 */
public class LocalFileStorage implements FileStorage {

    private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final Path root;

    public LocalFileStorage(Path root) throws IOException {
        this.root = Files.createDirectories(root).toRealPath();
    }

    @Override
    public void put(String key, InputStream content) throws IOException {
        Path target = resolve(key);
        Path temp = Files.createTempFile(root, "upload-", ".part");
        try {
            Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Chave de arquivo invalida");
        }
        // Dois níveis de pasta pelos primeiros caracteres, para não acumular milhares de arquivos numa pasta só.
        Path path = root.resolve(key.substring(0, 2)).resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Chave de arquivo invalida");
        }
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel preparar o storage", e);
        }
        return path;
    }
}
