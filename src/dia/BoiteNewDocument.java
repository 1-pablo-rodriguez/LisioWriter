package dia;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import act.select_CANCEL;
import act.select_OK;
import writer.commandes;
import writer.playSound;
import writer.ui.EditorFrame;

public class BoiteNewDocument  {
	
	private static JDialog dialog = new JDialog((Frame) null, "Créer un nouveau document", true);
	EditorFrame parent ;
	 
	public BoiteNewDocument(EditorFrame parent) {
		 
		this.parent = parent;
		
		dialog = new JDialog((Frame) null, "Créer un nouveau document", true);
        dialog.setLayout(new BorderLayout());
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        // Crée un panneau pour ajouter le champ de texte
        JPanel panel = new JPanel();
        // Crée un champ de texte (JTextField)
        JTextField textField = new JTextField(20);
        textField.getAccessibleContext().setAccessibleName("Zone de texte");
        textField.getAccessibleContext().setAccessibleDescription("Tapez un nom pour le nouveau fichier.");
        
        panel.add(new JLabel("Entrez un nom de fichier :"));
        panel.add(textField);
        // Ajoute le panneau à la boîte de dialogue
        dialog.add(panel, BorderLayout.CENTER);

        textField.setText("");
        
        // Crée une regex pour vérifier les caractères autorisés (lettres, chiffres, espaces)
        String regex = "^[a-zA-Z0-9 _-]+$";
        Pattern pattern = Pattern.compile(regex);

        // Ajoute un KeyListener pour bloquer les caractères spéciaux lors de la saisie
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                // Vérifie si le caractère saisi n'est pas valide
                if (!pattern.matcher(Character.toString(c)).matches()) {
                    e.consume(); // Empêche l'insertion du caractère
                }
            }
            
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // Valide la saisie lorsque la touche "Entrée" est appuyée
                    String texte = textField.getText();
                    if (texte.isBlank()) {
                    	 if(commandes.audioActif) new playSound(commandes.getPathApp + "/erreur_nom_fichier.wav");
                    } else {
                        commandes.nameFile = texte;
                        commandes.hash=0;
                        
                        commandes.nodeblindWriter.retourneFirstEnfant("contentText").getContenu().clear();
                       	commandes.nodeblindWriter.getAttributs().put("filename","nouveaux fichier blindWriter");
                        commandes.nameFile = texte;
                        
                        commandes.defaultStyles();
                        
                        parent.getEditor().setText("");
                        if(commandes.audioActif) new playSound(commandes.getPathApp + "/nouveau_document_vierge.wav");
                        fermeture();  // Ferme la boîte de dialogue
                    }
                }
            }
        });
        
        // Action à exécuter lorsque le bouton OK obtient le focus
        textField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                //if(commandes.audioActif) new playSound(commandes.getPathApp + "/nom_nouveau_fichier.wav");
            }

            @Override
            public void focusLost(FocusEvent e) {
                // Rien à faire lorsque le focus est perdu
            }
        });

        // Boutons OK et Annuler
        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton("OK");
        okButton.getAccessibleContext().setAccessibleName("OK");
        
        JButton cancelButton = new JButton("Annuler");
        cancelButton.getAccessibleContext().setAccessibleName("Annuler");
        
        // Action à exécuter lorsque le bouton OK obtient le focus
        okButton.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                // Exécuter l'action lorsque le bouton OK obtient le focus
                AbstractAction okFocusAction = new select_OK();
                okFocusAction.actionPerformed(null);  // Appelle l'action directement
            }

            @Override
            public void focusLost(FocusEvent e) {
                // Rien à faire lorsque le focus est perdu
            }
        });
        
        
        // Action pour le bouton OK
        okButton.addActionListener(e -> {
            String texte = textField.getText();
            if (texte.isBlank()) {
            	System.out.println("Création d'un document vierge impossible");
            } else {
            	System.out.println("Création d'un document vierge");
                commandes.nameFile=texte;
                commandes.hash=0;
                parent.getEditor().setText("");
                fermeture();
            }
        });
        
        // Entrée sur le bouton OK
        okButton.addKeyListener(new KeyAdapter() {
        	@Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // Valide la saisie lorsque la touche "Entrée" est appuyée
                    String texte = textField.getText();
                    if (texte.isBlank()) {
                    	System.out.println("Création d'un document vierge impossible");
                    } else {
                    	System.out.println("Création d'un document vierge");
                        commandes.nameFile = texte;
                        commandes.hash=0;
                        parent.getEditor().setText("");
                        fermeture();
                    }
                }
        	}
        });


        // Action pour le bouton Annuler
        cancelButton.addActionListener(e -> fermeture());
        
        // Action à exécuter lorsque le bouton OK obtient le focus
        cancelButton.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                // Exécuter l'action lorsque le bouton OK obtient le focus
                AbstractAction okFocusAction = new select_CANCEL();
                okFocusAction.actionPerformed(null);  // Appelle l'action directement
            }

            @Override
            public void focusLost(FocusEvent e) {
                // Rien à faire lorsque le focus est perdu
            }
        });
        
     // Entrée sur le bouton OK
        cancelButton.addKeyListener(new KeyAdapter() {
        	@Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                	if(commandes.audioActif) new playSound(commandes.getPathApp + "/cancel_nom_fichier.wav");
                	fermeture();  // Ferme la boîte de dialogue
                }
        	}
        });
        

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        // Ajoute les boutons à la boîte de dialogue
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        // Configure et affiche la boîte de dialogue
        dialog.pack();
        dialog.setLocationRelativeTo(null);

        // Assure que le JTextField a le focus quand la boîte apparaît
        SwingUtilities.invokeLater(textField::requestFocusInWindow);

        dialog.setVisible(true);
        dialog.setAlwaysOnTop(true);
        dialog.toFront();
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
}

