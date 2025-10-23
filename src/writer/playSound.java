package writer;

import java.io.File;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class playSound {
 public playSound(String soundFile) {
	 try {
         // Ouvrir un fichier audio (wav ou autre format supporté)
         File audioFile = new File(soundFile);
         AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);

         // Obtenir un Clip pour jouer le son
         Clip clip = AudioSystem.getClip();
         clip.open(audioStream);
         clip.start();  // Jouer le son
     } catch (Exception e) {
         e.printStackTrace();
     }
 }
}
