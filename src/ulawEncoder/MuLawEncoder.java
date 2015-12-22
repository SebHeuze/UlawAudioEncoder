package ulawEncoder;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * MuLawEncoder Convertit un fichier PCM en ULAW
 * @author SEB
 *
 */
public class MuLawEncoder {

    /**
     * Table de conversion remplaçant la formule mulaw pour des raisons de rapidité
     * floor(log2((abs(linear_sample)+132)/128))
     */
    private final static byte[] muLawCompressArray = new byte[]{
        0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3,
        4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
        5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
        5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7
    };
    
    /**
     * Fichier Entrant
     */
    private String inputFile;
    
    /**
     * Fichier sortant
     */
    private String outputFile;
    
    /**
     * 16 Bits signés de  -32634 à 32635
     */
    private final static int maxSampleValue = 32635; 
    
    /**
     * Amélioration audio atténuation
     */
    private final static short cBias = 132; 
    
    /**
     * Constructeur
     * @param inputFile
     * @param outputFile
     */
    public MuLawEncoder(String inputFile, String outputFile) {
       this.inputFile = inputFile;
       this.outputFile = outputFile;
    }

    /**
     * Fonction de conversion de 2 bytes
     * @param sample les 16bits à convertir
     * @return les 8 bits de resultats en ulaw
     */
    private byte linearToULawSample(short sample) {
        //On récupère le signe
        int sign = ((~sample) >> 8) & 0x80; //1000 0000 ou 0000 000

        //Si le signe est négatif 
        if (!(sign == 0x80)) {
          sample = (short) -sample;
        }
        //Borne max
        if (sample > maxSampleValue) {
            sample = maxSampleValue;
        }
        //Amélioration Audio
        sample =  (short) (sample + cBias);
        
        int exponent = (int) muLawCompressArray[(sample >> 7)];
        int mantissa = (sample >> (exponent + 3)) & 0x0F;
        int s = (sign | exponent << 4) | mantissa;
        s = ~s;
        
        return (byte) s;
    }
    
    /**
     * Démarrer la conversion
     */
    private void convert(){
      //Ouverture du fichier
      File file =new File(this.inputFile);
      
      //Offset ou commence la Data
      int offset = 0x28;

      //Si le fichier existe
      if(file.exists()){
        double fileSize = file.length();
        double dataSize = fileSize - offset;
        
        Path path = Paths.get(file.getAbsolutePath());
       
        try {
        //On récupère le contenu du fichier
          byte[] data;
          data = Files.readAllBytes(path);
        
        //Nombre de Blocs de 2 bytes (2 bytes = 16 bits => Format entrée)
          int count = (int) dataSize / 2; 
          //On crée le fichier de sortie (taille divisiée par 2)
          byte[] outputBuffer = new byte[count];
          short sample;
          
          //Pour chaque couple de 2 bytes (16 bits)
          for (int i = 0; i < count; i++) {
            //On récupère les 2 bytes
            sample = (short) (((data[offset++] & 0xff) | (data[offset++]) << 8));
            //On les convertis et on en récupère un nombre 8 bits
            outputBuffer[i] = this.linearToULawSample(sample);
          }
          
          
          OutputStream os;        
          os = new FileOutputStream(new File(this.outputFile));
          BufferedOutputStream bos = new BufferedOutputStream(os);
          DataOutputStream outFile = new DataOutputStream(bos);
          
          //Données du Header int = 32 bits, Short = 16 bits
          int nChunkSize =  count + 0x24;  //Taille Chunk
          int   nSubchunk1Size = 18;    //Taille restante header
          short sAudioFormat = 7; //7 = Ulaw
          short sChannel = 1; //Mono = 1
          int   nSampleRate = 8000; //8000Hz
          short sBitsPerSampl = 8; //8 Bits
          int   nByteRate = nSampleRate * sChannel * sBitsPerSampl / 8;
          short sBlockAlign = (short) (sChannel * sBitsPerSampl / 8);
          short sReserve = 0; //Bits reserve
          
          outFile.writeBytes("RIFF");                                 // 00 - RIFF
          outFile.write(intToByteArray((int)nChunkSize), 0, 4);      // 04 - Taille du reste du fichier
          outFile.writeBytes("WAVE");                                 // 08 - WAVE
          outFile.writeBytes("fmt ");                                 // 12 - fmt 
          outFile.write(intToByteArray(nSubchunk1Size), 0, 4);  // 16 - Taille restante header
          outFile.write(shortToByteArray(sAudioFormat), 0, 2);     // 20 - 7 = Ulaw
          outFile.write(shortToByteArray(sChannel), 0, 2);   // 22 - Mono
          outFile.write(intToByteArray(nSampleRate), 0, 4);     // 24 - 8000Hz
          outFile.write(intToByteArray(nByteRate), 0, 4);       // 28 - Bytes par seconde
          outFile.write(shortToByteArray(sBlockAlign), 0, 2); // 32 - Nombre de Bytes par donnée par channel(Sample Audio)
          outFile.write(shortToByteArray(sBitsPerSampl), 0, 2);  // 34 - Bits par sample
          outFile.write(shortToByteArray(sReserve), 0, 2);  // 34 - Reserve
          outFile.writeBytes("data");                                 // 36 - Données
          outFile.write(intToByteArray((int)count), 0, 4);       // 40 - Taille des données
          outFile.write(outputBuffer); //On écrit les données
          
        
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      } else {
        System.err.println("Fichier "+inputFile+" non trouvé");
      }
    }
 
    /**
     * Convertir un Integer vers un array de Bytes
     * @param i
     * @return
     */
    private byte[] intToByteArray(int i)
    {
        byte[] b = new byte[4];
        b[0] = (byte) (i & 0x00FF);
        b[1] = (byte) ((i >> 8) & 0x000000FF);
        b[2] = (byte) ((i >> 16) & 0x000000FF);
        b[3] = (byte) ((i >> 24) & 0x000000FF);
        return b;
    }
    
    /**
     * Convertir un short vers un array de Bytes
     * @param data
     * @return
     */
    public byte[] shortToByteArray(short data)
    {
        return new byte[]{(byte)(data & 0xff),(byte)((data >>> 8) & 0xff)};
    }
    
    /**
     * Main
     * @param args
     */
    public static void main (String[] args){
      MuLawEncoder muEncoder = new MuLawEncoder("testsoundpcm.wav","testsoundulaw.wav");
      muEncoder.convert();
    }
}

