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
import java.util.Arrays;


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
	private final static int MAX_SAMPLE_VALUE = 32635; 

	/**
	 * Amélioration audio atténuation
	 */
	private final static short C_BIAS = 132; 

	/**
	 * DATA header
	 */
	private final static byte[] DATA_HEADER = new byte[]{0x64,0x61,0x74,0x61}; //DATA HEADER

	/**
	 * Format AUDIO
	 */
	private final static short AUDIO_FORMAT = 7; //7 = Ulaw

	/**
	 * Nombre de Channel (Mono = 1, Stereo=2 etc..)
	 */
	private final static short NB_CHANNEL = 1; //Mono = 1

	/**
	 * Fréquence d'échantillonnage
	 */
	private final static int   SAMPLE_RATE = 8000; //8000Hz

	/**
	 * Nombre de bits par sample audio
	 */
	private final static short BITS_AUDIO = 8; //8 Bits

	/**
	 * Bytes par seconde
	 */
	private final static int   BYTE_RATE = SAMPLE_RATE * NB_CHANNEL * BITS_AUDIO / 8;

	/**
	 * Nombre de Bytes par sample par channel
	 */
	private final static short BYTE_SAMPLE_CHANNEl = (short) (NB_CHANNEL * BITS_AUDIO / 8);


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
		if (sample > MAX_SAMPLE_VALUE) {
			sample = MAX_SAMPLE_VALUE;
		}
		//Amélioration Audio
		sample =  (short) (sample + C_BIAS);

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



		//Si le fichier existe
		if(file.exists()){

			Path path = Paths.get(file.getAbsolutePath());

			try {
				//On récupère le contenu du fichier
				byte[] data;
				data = Files.readAllBytes(path);

				//Offset ou commence la Data
				int offset = indexOf(data, DATA_HEADER) + 8; // 8 = DATA + Taille données
				double fileSize = file.length();
				double dataSize = fileSize - offset;


				//Nombre de Blocs de 2 bytes (2 bytes = 16 bits => Format entrée)
				int count = (int) dataSize / 2; 
				//On crée le fichier de sortie (taille divisiée par 2)
				byte[] outputBuffer = new byte[count + offset];
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

				this.writeHeaders(outFile, count);
				outFile.write(outputBuffer); //On écrit les données
				outFile.close();

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			System.err.println("Fichier "+inputFile+" non trouvé");
		}
	}

	private void concatUlawFiles(String inputFile1, String inputFile2, String outputFile){

		//Ouverture du fichier 1
		File file1 =new File(inputFile1);
		//Ouverture du fichier 2
		File file2 =new File(inputFile2);

		//Si le fichier existe
		if(file1.exists() && file2.exists()){
			Path path1 = Paths.get(file1.getAbsolutePath());
			Path path2 = Paths.get(file2.getAbsolutePath());
			try {
				//On récupère le contenu du fichier
				byte[] fileContent1 = Files.readAllBytes(path1);
				byte[] fileContent2 = Files.readAllBytes(path2);

				int dataOffset1 = indexOf(fileContent1, DATA_HEADER);
				int dataOffset2 = indexOf(fileContent2, DATA_HEADER);
				
				byte[] data1 = Arrays.copyOfRange(fileContent1, dataOffset1 + 8, fileContent1.length-40); // 8 = DATA + Taille données 
				byte[] data2 =  Arrays.copyOfRange(fileContent2, dataOffset2 + 8, fileContent2.length-40);// 8 = DATA + Taille données

				byte[] dataConcat = concatByte(data1, data2);

				OutputStream os;        
				os = new FileOutputStream(new File(outputFile));
				BufferedOutputStream bos = new BufferedOutputStream(os);
				DataOutputStream outFile = new DataOutputStream(bos);

				this.writeHeaders(outFile, dataConcat.length);
				outFile.write(dataConcat); //On écrit les données
				outFile.close();

			} catch (IOException ioe) {
				System.err.println("Erreur lors de la lecture des fichiers ");
			}
		} else {
			System.err.println("Fichier " + file1 + " ou " + file2 + " non trouvés");
		}
	}

	private void writeHeaders(DataOutputStream outFile, int tailleData) throws IOException{
		//Données du Header int = 32 bits, Short = 16 bits
		int nChunkSize =  tailleData + 54;  //Taille Chunk
		int   nSubchunk1Size = 30;    //Taille restante header
		int factSize = 4;
		short sReserve = 0; //Bits reserve

		outFile.writeBytes("RIFF");                                 // 00 - RIFF
		outFile.write(intToByteArray((int)nChunkSize), 0, 4);      // 04 - Taille du reste du fichier
		outFile.writeBytes("WAVE");                                 // 08 - WAVE
		outFile.writeBytes("fmt ");                                 // 12 - fmt 
		outFile.write(intToByteArray(nSubchunk1Size), 0, 4);  // 16 - Taille restante header
		outFile.write(shortToByteArray(AUDIO_FORMAT), 0, 2);     // 20 - 7 = Ulaw
		outFile.write(shortToByteArray(NB_CHANNEL), 0, 2);   // 22 - Mono
		outFile.write(intToByteArray(SAMPLE_RATE), 0, 4);     // 24 - 8000Hz
		outFile.write(intToByteArray(BYTE_RATE), 0, 4);       // 28 - Bytes par seconde
		outFile.write(shortToByteArray(BYTE_SAMPLE_CHANNEl), 0, 2); // 32 - Nombre de Bytes par donnée par channel(Sample Audio)
		outFile.write(shortToByteArray(BITS_AUDIO), 0, 2);  // 34 - Bits par sample
		outFile.write(shortToByteArray(sReserve), 0, 2);  // 34 - Reserve
		outFile.writeBytes("fact");       // 40 - Taille des données
		outFile.write(intToByteArray(factSize), 0, 4); 
		outFile.write(intToByteArray((int)tailleData), 0, 4);   
		outFile.writeBytes("data");                                 // 36 - Données
		outFile.write(intToByteArray((int)tailleData), 0, 4);       // 40 - Taille des données
	}

	/**
	 * Concaténer 2 array
	 * @param array1
	 * @param array2
	 * @return
	 */
	private static byte[] concatByte(byte[] array1, byte[] array2){
		int length = array1.length + array2.length;
		byte[] result = new byte[length];
		System.arraycopy(array1, 0, result, 0, array1.length);
		System.arraycopy(array2, 0, result, array1.length, array2.length);
		return result;
	}



	/**
	 * Convertir un Integer vers un array de Bytes
	 * @param i
	 * @return
	 */
	private static byte[] intToByteArray(int i)
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
	public static byte[] shortToByteArray(short data)
	{
		return new byte[]{(byte)(data & 0xff),(byte)((data >>> 8) & 0xff)};
	}

	/**
	 * Rechercher d'un array de Byte dans un array de byte
	 * @param outerArray
	 * @param smallerArray
	 * @return
	 */
	public static int indexOf(byte[] outerArray, byte[] smallerArray) {
		for(int i = 0; i < outerArray.length - smallerArray.length+1; ++i) {
			boolean found = true;
			for(int j = 0; j < smallerArray.length; ++j) {
				if (outerArray[i+j] != smallerArray[j]) {
					found = false;
					break;
				}
			}
			if (found) return i;
		}
		return -1;  
	}  

	/**
	 * Main
	 * @param args
	 */
	public static void main (String[] args){
		MuLawEncoder muEncoder = new MuLawEncoder("Bordure_quai.wav","Bordure_quaiu.wav");
		muEncoder.convert();

		muEncoder.concatUlawFiles("ulawjingle.wav", "Bordure_quaiu.wav", "concat.wav");
	}
}

