import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Versão A — Multiplicação paralela com Processos.
 *
 * Estratégia de comunicação (IPC via arquivos binários temporários):
 *   1. Processo pai serializa A e B em /tmp/matrix_proc_XXXXX/input.bin
 *   2. Pai lança N instâncias de ProcessWorker via ProcessBuilder,
 *      passando o caminho do arquivo e o intervalo de linhas como argumentos.
 *   3. Cada processo filho lê o arquivo compartilhado, calcula suas linhas
 *      e escreve o resultado em /tmp/matrix_proc_XXXXX/output_p.bin
 *   4. Pai aguarda todos os filhos, lê os resultados parciais e monta C.
 *
 * Nota: o tempo medido inclui o overhead de I/O (serialização + lançamento de JVM),
 * que é inerente ao modelo de processos separados — não há memória compartilhada.
 */
public class ProcessMultiplier {

    /**
     * Multiplica A × B usando {@code numProcesses} JVMs filhas.
     *
     * @param a           matriz A (n × n)
     * @param b           matriz B (n × n)
     * @param n           dimensão das matrizes
     * @param numProcesses número de processos filhos
     * @return            matriz resultado C (n × n)
     */
    public static double[][] multiply(double[][] a, double[][] b, int n, int numProcesses)
            throws Exception {

        Path   tmpDir     = null;
        File   inputFile  = null;
        File[] outputFiles = new File[numProcesses];
        Process[] procs   = new Process[numProcesses];

        try {
            // Cria diretório temporário exclusivo para este run
            tmpDir    = Files.createTempDirectory("matrix_proc_");
            inputFile = tmpDir.resolve("input.bin").toFile();

            // Serializa A e B uma única vez — todos os filhos leem o mesmo arquivo
            MatrixUtils.writeMatricesToFile(a, b, n, inputFile);

            // Descobre o executável java e o classpath da JVM atual
            String javaExe   = System.getProperty("java.home")
                             + File.separator + "bin" + File.separator + "java";
            String classpath = System.getProperty("java.class.path");

            int rowsPerProcess = n / numProcesses;

            // Lança os processos filhos
            for (int p = 0; p < numProcesses; p++) {
                int startRow = p * rowsPerProcess;
                // último processo absorve linhas residuais se n não for divisível
                int endRow = (p == numProcesses - 1) ? n : startRow + rowsPerProcess;

                outputFiles[p] = tmpDir.resolve("output_" + p + ".bin").toFile();

                ProcessBuilder pb = new ProcessBuilder(
                        javaExe,
                        "-Xmx512m",           // memória para o filho (suficiente para 2 matrizes N×N)
                        "-cp", classpath,
                        "ProcessWorker",
                        inputFile.getAbsolutePath(),
                        String.valueOf(startRow),
                        String.valueOf(endRow),
                        outputFiles[p].getAbsolutePath()
                );

                // stdout do filho descartado; stderr herdado → erros aparecem no terminal do pai
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);

                procs[p] = pb.start();
            }

            // Aguarda todos os filhos concluírem (sem polling — waitFor() bloqueia)
            for (int p = 0; p < numProcesses; p++) {
                int exitCode = procs[p].waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException(
                        "Processo filho " + p + " encerrou com código " + exitCode);
                }
            }

            // Monta a matriz resultado a partir dos arquivos parciais
            double[][] c = new double[n][n];
            for (int p = 0; p < numProcesses; p++) {
                MatrixUtils.readPartialResult(c, outputFiles[p]);
            }

            return c;

        } finally {
            // Limpeza segura — garante remoção mesmo que uma exceção tenha ocorrido
            for (File f : outputFiles) {
                if (f != null && f.exists()) f.delete();
            }
            if (inputFile != null && inputFile.exists()) inputFile.delete();
            if (tmpDir != null) tmpDir.toFile().delete();
        }
    }
}
