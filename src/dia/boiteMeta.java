package dia;

import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import writer.commandes;
import writer.ui.EditorFrame;


public class boiteMeta {
	private static JDialog dialog = new JDialog((Frame) null, "Modifier le nom de fichier", true);
	private static JTextField sujetField = new JTextField(20);
	private static JTextField titreField = new JTextField(20);
	private static JTextField auteurField = new JTextField(20);
	private static JTextField coauteurField = new JTextField(20);
	private static JTextField societyField = new JTextField(20);
	
	private final EditorFrame parent;
	
	public boiteMeta(EditorFrame parent) {
		this.parent = parent;
		dialog = new JDialog((Frame) null, "Modifier les méta-données", true);
        dialog.setLayout(new GridLayout(5, 0));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        
        dialog.getContentPane().add(new JLabel("Entrez le titre :"));
        titreField.setText(commandes.meta.retourneFirstEnfant("titre").getAttributs().get("LeTitre"));
        titreField.getAccessibleContext().setAccessibleName("Titre.");
        dialog.getContentPane().add(titreField);
        
        dialog.getContentPane().add(new JLabel("Entrez le sujet :"));
        sujetField.setText(commandes.meta.retourneFirstEnfant("sujet").getAttributs().get("LeSujet"));
        sujetField.getAccessibleContext().setAccessibleName("Sujet.");
        dialog.getContentPane().add(sujetField);
        
        dialog.getContentPane().add(new JLabel("Entrez l'auteur :"));
        auteurField.setText(commandes.meta.retourneFirstEnfant("auteur").getAttributs().get("nom"));
        auteurField.getAccessibleContext().setAccessibleName("Nom de l'auteur.");
        dialog.getContentPane().add(auteurField);
        
        dialog.getContentPane().add(new JLabel("Entrez le coauteur :"));
        coauteurField.setText(commandes.meta.retourneFirstEnfant("coauteur").getAttributs().get("nom"));
        coauteurField.getAccessibleContext().setAccessibleName("Nom du coauteur.");
        dialog.getContentPane().add(coauteurField);
        
        dialog.getContentPane().add(new JLabel("Entrez le nom de votre société :"));
        societyField.setText(commandes.meta.retourneFirstEnfant("coauteur").getAttributs().get("nom"));
        societyField.getAccessibleContext().setAccessibleName("Nom de votre société, ou entreprise.");
        dialog.getContentPane().add(societyField);
     

        // Ajoute un KeyListener pour bloquer les caractères spéciaux lors de la saisie
        titreField.addKeyListener(new KeyAdapter() {
            @Override
                 public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    valide();
                }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    fermeture();  // Ferme la boîte de dialogue
                }
                if(e.getKeyCode() == KeyEvent.VK_DOWN) {
                	sujetField.requestFocus();
                }
                if(e.getKeyCode() == KeyEvent.VK_UP) {
                	coauteurField.requestFocus();
                }
            } 
        });
        
        // Ajoute un KeyListener pour bloquer les caractères spéciaux lors de la saisie
        sujetField.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // Valide la saisie lorsque la touche "Entrée" est appuyée
                    valide();
                }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    // Valide la saisie lorsque la touche "Echappe" est appuyée
                	fermeture();
                }
                if(e.getKeyCode() == KeyEvent.VK_UP) {
                	titreField.requestFocus();
                }
                if(e.getKeyCode() == KeyEvent.VK_DOWN) {
                	auteurField.requestFocus();
                }
            } 
        });
        
        // Ajoute un KeyListener pour bloquer les caractères spéciaux lors de la saisie
        auteurField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // Valide la saisie lorsque la touche "Entrée" est appuyée
                    valide();
                }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    // Valide la saisie lorsque la touche "Echappe" est appuyée
                	fermeture();
                }
                if(e.getKeyCode() == KeyEvent.VK_UP) {
                	sujetField.requestFocus();
                }
                if(e.getKeyCode() == KeyEvent.VK_DOWN) {
                	coauteurField.requestFocus();
                }
            } 
        });
        
        // Ajoute un KeyListener pour bloquer les caractères spéciaux lors de la saisie
        coauteurField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // Valide la saisie lorsque la touche "Entrée" est appuyée
                    valide();
                }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    // Valide la saisie lorsque la touche "Echappe" est appuyée
                	fermeture();
                }
                if(e.getKeyCode() == KeyEvent.VK_UP) {
                	auteurField.requestFocus();
                }
                if(e.getKeyCode() == KeyEvent.VK_DOWN) {
                	societyField.requestFocus();
                }
                
            } 
        });
        
        
        // Ajoute un KeyListener pour bloquer les caractères spéciaux lors de la saisie
        societyField.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // Valide la saisie lorsque la touche "Entrée" est appuyée
                    valide();
                }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    // Valide la saisie lorsque la touche "Echappe" est appuyée
                	fermeture();
                }
                if(e.getKeyCode() == KeyEvent.VK_UP) {
                	coauteurField.requestFocus();
                }
                if(e.getKeyCode() == KeyEvent.VK_DOWN) {
                	titreField.requestFocus();
                }
            } 
        });
        
        
        
        // Configure et affiche la boîte de dialogue
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        
        // Assure que le JTextField a le focus quand la boîte apparaît
        SwingUtilities.invokeLater(titreField::requestFocusInWindow);
        dialog.setVisible(true);
     
        
	}
	
	/** 
	 * fermeture de la dialog
	 */
	private void fermeture() {
        if (parent != null) {
            SwingUtilities.invokeLater(() -> {
                parent.requestFocus();              // redonne le focus à la frame
                parent.getEditor().requestFocusInWindow(); // et au JTextArea
            });
        }
        dialog.dispose();
    }
	
	private void valide() {
		 commandes.meta.retourneFirstEnfant("titre").getAttributs().put("LeTitre", titreField.getText().trim());
		 commandes.meta.retourneFirstEnfant("sujet").getAttributs().put("LeSujet", sujetField.getText().trim());
		 commandes.meta.retourneFirstEnfant("auteur").getAttributs().put("nom", auteurField.getText().trim());
		 commandes.meta.retourneFirstEnfant("coauteur").getAttributs().put("nom", auteurField.getText().trim());
		 commandes.meta.retourneFirstEnfant("society").getAttributs().put("nom", societyField.getText().trim());
		 fermeture();
	}
	
}
