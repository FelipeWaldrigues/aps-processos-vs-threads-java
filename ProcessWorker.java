import java.io.File;

/**
 * Processo filho — executado em uma JVM separada pelo ProcessMultiplier.
 *
 * Argumentos (via linha de comando):
 *   [0] caminho absoluto do arquivo de entrada (input.bin)  — contém A e B
 *   [1] startRow  — primeira linha (inclusive) a calcular
 *   [2] endRow    — última linha (exclusive) a calcular
 *   [3] caminho absoluto do arquivo de saída (output_p.bin) — resultado parcial
 *
 * Lê A e B do arquivo compartilhado, calcula as linhas atribuídas de C = A × B
 * e serializa o resultado parcial no arquivo de saída.
 *
 * Cada instância roda em memória isolada (sem compartilhamento com o processo pai).
 */
public class ProcessWorker {

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("[ProcessWorker] Uso: ProcessWorker <inputFile> <startRow> <endRow> <outputFile>");
            System.exit(1);
        }

        File inputFile  = new File(args[0]);
        int  startRow   = Integer.parseInt(args[1]);
        int  endRow     = Integer.parseInt(args[2]);
        File outputFile = new File(args[3]);

        // Lê matrizes A e B do arquivo compartilhado (escrito pelo processo pai)
        double[][][] matrices = MatrixUtils.readMatricesFromFile(inputFile);
        double[][] a = matrices[0];
        double[][] b = matrices[1];
        int n = a.length;

        // Aloca apenas as linhas necessárias (otimização de memória não aplicada aqui
        // para manter a serialização simples — c[i][j] = 0 para linhas fora do range)
        double[][] c = new double[n][n];

        // Kernel de multiplicação ikj para as linhas [startRow, endRow)
        for (int i = startRow; i < endRow; i++) {
            for (int k = 0; k < n; k++) {
                double aik = a[i][k];
                for (int j = 0; j < n; j++) {
                    c[i][j] += aik * b[k][j];
                }
            }
        }

        // Escreve somente as linhas calculadas no arquivo de saída
        MatrixUtils.writePartialResult(c, startRow, endRow, n, outputFile);
    }
}
