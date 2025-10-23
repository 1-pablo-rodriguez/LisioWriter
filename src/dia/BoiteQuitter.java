package dia;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import writer.blindWriter;


public class BoiteQuitter {

	private static JDialog dialog = new JDialog((Frame) null, "Quitter", true);
	
	@SuppressWarnings("serial")
	public BoiteQuitter() {
		
		//String text = blindWriter.editorPane.getText();
        //int newHash = text.hashCode();
        String message = "Voulez-vous quitter blinWriter ?";
        blindWriter.announceCaretLine(true, true, message);
        
		
		
 		dialog = new JDialog((Frame) null, "Quitter", true);
		dialog.setLayout(new BorderLayout());
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		
		JPanel panel = new JPanel();
		JLabel label = new JLabel("<html>" + message +"</html>");
		label.getAccessibleContext().setAccessibleDescription(message);
		
		panel.add(label);
	    dialog.add(panel, BorderLayout.CENTER);
		 
        JPanel buttonPanel = new JPanel();
        
        JButton okButton = new JButton("Quitter");
        okButton.getAccessibleContext().setAccessibleName("Quitter");
        
        
        JButton cancelButton = new JButton("Annuler");
        cancelButton.getAccessibleContext().setAccessibleName("Annuler");
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
       
        
        // Action pour le bouton OK
        okButton.addActionListener(e -> {
        	fermeture();
        });
        
        // Entrée sur le bouton OK
        okButton.addKeyListener(new KeyAdapter() {
        	@Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // Valide la saisie lorsque la touche "Entrée" est appuyée
                    fermeture();
                }
        	}
        });
        okButton.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                System.out.println("🔵 Le bouton 'Quitter' a le focus.");
                blindWriter.announceCaretLine(true, true, "Bouton : Quitter");
            }

            @Override
            public void focusLost(FocusEvent e) {
                System.out.println("⚪ Le bouton 'Quitter' a perdu le focus.");
            }
        });


        // Action pour le bouton Annuler
        cancelButton.addActionListener(e -> Annuler());
        
     // Entrée sur le bouton OK
        cancelButton.addKeyListener(new KeyAdapter() {
        	@Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                	Annuler();  // Ferme la boîte de dialogue
                }
        	}
        });
        cancelButton.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                System.out.println("🔵 Le bouton 'Annuler' a le focus.");
                blindWriter.announceCaretLine(true, true, "Bouton : Annuler");
            }

            @Override
            public void focusLost(FocusEvent e) {
                System.out.println("⚪ Le bouton 'Annuler' a perdu le focus.");
            }
        });
       
        // Ajoute les boutons à la boîte de dialogue
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        // Configure et affiche la boîte de dialogue
        dialog.pack();
        dialog.setLocationRelativeTo(null);
      
        SwingUtilities.invokeLater(() -> {
            // configurer les flèches après affichage
            setupFocusNavigation(okButton, cancelButton);
            okButton.requestFocusInWindow(); // focus initial
        });
        
        
        dialog.setVisible(true);
        dialog.setAlwaysOnTop(true);
        dialog.toFront();
        
        label.setFocusable(true);
        
     // Créer la liste des composants navigables
    	List<Component> focusables = List.of(okButton, cancelButton);
    	
    	for (Component comp : focusables) {
    	    if (comp instanceof JComponent jc) {
    	        final JComponent target = jc; // ✅ capture correcte

    	        target.setFocusable(true);
    	        target.setFocusTraversalKeysEnabled(false);

    	        target.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("RIGHT"), "nextFocus");
    	        target.getActionMap().put("nextFocus", new AbstractAction() {
    	            @Override
    	            public void actionPerformed(ActionEvent e) {
    	                int i = focusables.indexOf(target);
    	                int next = (i + 1) % focusables.size();
    	                focusables.get(next).requestFocusInWindow();
    	            }
    	        });

    	        target.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("LEFT"), "prevFocus");
    	        target.getActionMap().put("prevFocus", new AbstractAction() {
    	            @Override
    	            public void actionPerformed(ActionEvent e) {
    	                int i = focusables.indexOf(target);
    	                int prev = (i - 1 + focusables.size()) % focusables.size();
    	                focusables.get(prev).requestFocusInWindow();
    	            }
    	        });
    	    }
    	}  
	}
	
	
	/** 
	 * fermeture de la dialog
	 */
	private void Annuler() {
		blindWriter.announceCaretLine(true, true, "Retour sur blindWriter.");
		blindWriter.getInstance();
		dialog.dispose();
	}
	
	/** 
	 * fermeture de la dialog
	 * @throws InterruptedException 
	 */
	private void fermeture(){
		blindWriter.announceCaretLine(true, true, "Fermeture de blindWriter.");
        dialog.setVisible(false);  // S'assurer qu'elle est invisible
        dialog.dispose();
        blindWriter.getInstance();
		System.exit(0); // Quitter l'application
	}
	
	
	@SuppressWarnings("serial")
	private void setupFocusNavigation(JComponent... components) {
	    List<JComponent> focusables = List.of(components);

	    for (int index = 0; index < focusables.size(); index++) {
	        JComponent jc = focusables.get(index);
	        final int currentIndex = index;

	        //jc.setFocusTraversalKeysEnabled(false);
	        jc.setFocusable(true);

	        jc.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("RIGHT"), "nextFocus");
	        jc.getActionMap().put("nextFocus", new AbstractAction() {
	            @Override
	            public void actionPerformed(ActionEvent e) {
	                int next = (currentIndex + 1) % focusables.size();
	                focusables.get(next).requestFocusInWindow();
	            }
	        });

	        jc.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("LEFT"), "prevFocus");
	        jc.getActionMap().put("prevFocus", new AbstractAction() {
	            @Override
	            public void actionPerformed(ActionEvent e) {
	                int prev = (currentIndex - 1 + focusables.size()) % focusables.size();
	                focusables.get(prev).requestFocusInWindow();
	            }
	        });
	    }
	}
	
}
