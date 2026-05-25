import java.io.*;
import java.util.Random;

/**
 * Utilitários centrais: geração de matrizes, multiplicação sequencial (referência),
 * validação de corretude e serialização binária para comunicação entre processos.
 */
public class MatrixUtils {

    // =========================================================================
    //  CONFIGURAÇÃO CENTRAL — altere aqui para mudar o tamanho das matrizes
    // =========================================================================
    public static final int N = 1000;   // experimente: 1500 ou 2000

    /**
     * Gera uma matriz n×n preenchida com doubles aleatórios.
     *
     * @param seed semente fixa para reprodutibilidade entre execuções
     */
    public static double[][] generate(int n, long seed) {
        Random rng = new Random(seed);
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                m[i][j] = rng.nextDouble() * 100.0;
        return m;
    }

    /**
     * Multiplicação sequencial ikj (cache-friendly).
     * Serve como gabarito para validar as versões paralelas.
     */
    public static double[][] multiplySequential(double[][] a, double[][] b, int n) {
        double[][] c = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < n; k++) {
                double aik = a[i][k];           // evita re-leitura de a[i][k] no loop interno
                for (int j = 0; j < n; j++) {
                    c[i][j] += aik * b[k][j];
                }
            }
        }
        return c;
    }

    /**
     * Compara element-a-elemento com tolerância epsilon.
     *
     * @return true se todas as diferenças estiverem abaixo de epsilon
     */
    public static boolean validate(double[][] expected, double[][] actual, int n, double epsilon) {
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (Math.abs(expected[i][j] - actual[i][j]) > epsilon)
                    return false;
        return true;
    }

    // =========================================================================
    //  I/O binário para comunicação entre processos via arquivos temporários
    //
    //  Formato do arquivo de entrada (input.bin):
    //    [int n] [n*n doubles de A] [n*n doubles de B]
    //
    //  Formato do arquivo de saída de cada worker (output_p.bin):
    //    [int startRow] [int endRow] [int n] [(endRow-startRow)*n doubles]
    // =========================================================================

    /** Serializa A e B em um único arquivo binário compartilhado pelos workers. */
    public static void writeMatricesToFile(double[][] a, double[][] b, int n, File file)
            throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file), 1 << 17))) {
            dos.writeInt(n);
            writeMatrix(dos, a, n);
            writeMatrix(dos, b, n);
        }
    }

    /**
     * Lê o arquivo de entrada e devolve [A, B].
     * Chamado pelos processos filhos (ProcessWorker).
     */
    public static double[][][] readMatricesFromFile(File file) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file), 1 << 17))) {
            int n = dis.readInt();
            double[][] a = readMatrix(dis, n);
            double[][] b = readMatrix(dis, n);
            return new double[][][]{a, b};
        }
    }

    /** Escreve o resultado parcial (linhas [startRow, endRow)) no arquivo de saída do worker. */
    public static void writePartialResult(double[][] c, int startRow, int endRow, int n, File file)
            throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file), 1 << 17))) {
            dos.writeInt(startRow);
            dos.writeInt(endRow);
            dos.writeInt(n);
            for (int i = startRow; i < endRow; i++)
                for (int j = 0; j < n; j++)
                    dos.writeDouble(c[i][j]);
        }
    }

    /** Lê um arquivo de resultado parcial e preenche as linhas correspondentes em c. */
    public static void readPartialResult(double[][] c, File file) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file), 1 << 17))) {
            int startRow = dis.readInt();
            int endRow   = dis.readInt();
            int n        = dis.readInt();
            for (int i = startRow; i < endRow; i++)
                for (int j = 0; j < n; j++)
                    c[i][j] = dis.readDouble();
        }
    }

    // --- helpers privados de I/O ---

    private static void writeMatrix(DataOutputStream dos, double[][] m, int n) throws IOException {
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                dos.writeDouble(m[i][j]);
    }

    private static double[][] readMatrix(DataInputStream dis, int n) throws IOException {
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                m[i][j] = dis.readDouble();
        return m;
    }
}
