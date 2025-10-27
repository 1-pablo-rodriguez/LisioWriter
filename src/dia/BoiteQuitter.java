package dia;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;

import writer.enregistre;
import writer.ui.EditorFrame;

public class BoiteQuitter {

    private static JDialog dialog;
    private EditorFrame parent;

    @SuppressWarnings("serial")
	public BoiteQuitter(EditorFrame parent) {

    	this.parent = parent;
    	
        final boolean modified = hasUnsavedChanges();
        final String title = "Quitter";
        final String baseMessage = modified
                ? "Des modifications non enregistrées ont été détectées."
                : "Voulez-vous quitter blindWriter ?";
        final String detail = modified
                ? "\nChoisissez : Enregistrer, Quitter sans enregistrer, ou Annuler.\n"
                : "\nDeux choix : Quitter ou Annuler.\n";
        final String fullMessage = modified ? (baseMessage + " " + detail) : baseMessage;


        dialog = new JDialog((Frame) null, title, true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(12, 12));

        // ---------- Style accessible ----------
        final int basePt = 18;
        Font big = new Font("Segoe UI", Font.PLAIN, basePt);
        Font bigBold = big.deriveFont(Font.BOLD);
        Color bg = Color.WHITE;
        Color fg = Color.BLACK;
        Color accent = new Color(0, 95, 204);

     // ---------- Message ----------
        JTextArea label = new JTextArea(fullMessage);

        // accessibilité & rendu
        label.setFont(bigBold);
        label.setForeground(fg);
        label.setBackground(bg);
        label.setLineWrap(true);
        label.setWrapStyleWord(true);

        // IMPORTANT : non éditable + focusable (pour lecture par lecteur d'écran)
        label.setEditable(false);
        label.setFocusable(true);

        // éviter caret/selection visibles (confort basse vision)
        label.setHighlighter(null);
        label.setCaretPosition(0);

        // Focus visuel
        label.setBorder(new LineBorder(bg, 0)); // neutre
        label.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                label.setBorder(new LineBorder(accent, 3, true));
            }
            @Override public void focusLost(FocusEvent e) {
                label.setBorder(new LineBorder(bg, 0));
            }
        });

        // ALT+M : donner le focus au label
        KeyStroke toMsg = KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.ALT_DOWN_MASK);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
              .put(toMsg, "focus-message");
        dialog.getRootPane().getActionMap().put("focus-message", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                label.requestFocusInWindow();
            }
        });

        // TAB/SHIFT+TAB : transférer le focus (ne pas insérer de tab dans le texte)
        bindTabToFocusTraversal(label);



        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createEmptyBorder(20, 24, 8, 24));
        center.setBackground(bg);
        center.add(label, BorderLayout.CENTER);
        dialog.add(center, BorderLayout.CENTER);

        // ---------- Boutons ----------
        JButton primaryBtn; // par défaut variable selon cas
        JPanel south = new JPanel();
        south.setBorder(BorderFactory.createEmptyBorder(0, 24, 20, 24));
        south.setBackground(bg);

        if (modified) {
            JButton saveBtn = new JButton("Enregistrer & Quitter");
            JButton discardBtn = new JButton("Quitter sans enregistrer");
            JButton cancelBtn = new JButton("Annuler");

            styleButton(saveBtn, bigBold, fg, accent);
            styleButton(discardBtn, big, fg, accent);
            styleButton(cancelBtn, big, fg, accent);

            // Mnémos
            saveBtn.setMnemonic(KeyEvent.VK_E);
            discardBtn.setMnemonic(KeyEvent.VK_S); // Sans enregistrer
            cancelBtn.setMnemonic(KeyEvent.VK_A);

            // Accessibles
            saveBtn.getAccessibleContext().setAccessibleName("Enregistrer et quitter");
            saveBtn.getAccessibleContext().setAccessibleDescription("Enregistrer le document avant de quitter");
            discardBtn.getAccessibleContext().setAccessibleName("Quitter sans enregistrer");
            discardBtn.getAccessibleContext().setAccessibleDescription("Fermer l'application sans sauvegarder");
            cancelBtn.getAccessibleContext().setAccessibleName("Annuler");
            cancelBtn.getAccessibleContext().setAccessibleDescription("Revenir à l'éditeur");

            // Actions
            saveBtn.addActionListener(e ->  doSaveAndQuit());
            discardBtn.addActionListener(e -> fermeture());
            cancelBtn.addActionListener(e -> annuler());

            addEnterKeyClicks(saveBtn, discardBtn, cancelBtn);

            south.add(saveBtn);
            south.add(discardBtn);
            south.add(cancelBtn);

            primaryBtn = saveBtn; // défaut = Enregistrer
            //setupArrowNavigation(label, saveBtn, discardBtn, cancelBtn);

        } else {
            JButton quitBtn = new JButton("Quitter");
            JButton cancelBtn = new JButton("Annuler");

            styleButton(quitBtn, bigBold, fg, accent);
            styleButton(cancelBtn, big, fg, accent);

            quitBtn.setMnemonic(KeyEvent.VK_Q);
            cancelBtn.setMnemonic(KeyEvent.VK_A);

            quitBtn.getAccessibleContext().setAccessibleName("Quitter l'application");
            quitBtn.getAccessibleContext().setAccessibleDescription("Fermer blindWriter");
            cancelBtn.getAccessibleContext().setAccessibleName("Annuler");
            cancelBtn.getAccessibleContext().setAccessibleDescription("Revenir à l'éditeur");

            quitBtn.addActionListener(e -> fermeture());
            cancelBtn.addActionListener(e -> annuler());

            addEnterKeyClicks(quitBtn, cancelBtn);

            south.add(quitBtn);
            south.add(cancelBtn);

            primaryBtn = quitBtn;
            //setupArrowNavigation(label, quitBtn, cancelBtn);

        }

        dialog.add(south, BorderLayout.SOUTH);

        // Entrée = bouton par défaut ; Échap = Annuler
        dialog.getRootPane().setDefaultButton(primaryBtn);
        bindEscapeTo(dialog.getRootPane(), this::annuler);

        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
            	SwingUtilities.invokeLater(() -> primaryBtn.requestFocusInWindow());
            }
            @Override public void windowClosing(WindowEvent e) {
                annuler();
            }
        });

        dialog.pack();
        dialog.setMinimumSize(new Dimension(520, dialog.getHeight()));
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    // ---------- Détection des modifications ----------
    private boolean hasUnsavedChanges() {
    	 return parent.isModifier();
    }


    // ---------- Utilitaires UI / A11y ----------
    private void styleButton(JButton b, Font f, Color fg, Color accent) {
        b.setFont(f);
        b.setPreferredSize(new Dimension(200, 44));
        b.setFocusPainted(true);
        b.setBackground(Color.WHITE);
        b.setForeground(fg);
        b.setBorder(new LineBorder(fg, 2, true));
        b.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                b.setBorder(new LineBorder(accent, 3, true));
            }
            @Override public void focusLost(FocusEvent e) {
                b.setBorder(new LineBorder(fg, 2, true));
            }
        });
    }

    private void addEnterKeyClicks(JButton... buttons) {
        KeyAdapter enter = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    ((JButton) e.getSource()).doClick();
                }
            }
        };
        for (JButton b : buttons) b.addKeyListener(enter);
    }

    @SuppressWarnings("serial")
	private void bindEscapeTo(JRootPane root, Runnable action) {
        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(esc, "esc-cancel");
        root.getActionMap().put("esc-cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }


    // ---------- Actions ----------
    private void annuler() {
        if (dialog != null) dialog.dispose();
        SwingUtilities.invokeLater(() -> {
            parent.requestFocus();
            parent.getEditor().requestFocusInWindow();
        });
    }

    private void fermeture() {
    	 SwingUtilities.invokeLater(() -> {
             parent.requestFocus();
             parent.getEditor().requestFocusInWindow();
         });
        System.exit(0);
    }
    
    
    /** Enregistre, marque le doc comme non modifié, met à jour le titre, puis quitte. */
    private void doSaveAndQuit() {
        try {
            // 1) lancer l'enregistrement (ta classe/commande existante)
            new enregistre(parent); 

            // 2) indiquer qu'il n'y a plus de modifications et MAJ du titre
            parent.setModified(false);

            // 3) fermer l'application
            fermeture();

        } catch (Throwable ex) {
            // Si quelque chose échoue, on prévient et on reste dans l'éditeur
        	System.out.println("Erreur pendant l'enregistrement. L'application reste ouverte.");
            ex.printStackTrace();
            annuler();
        }
    }
    
    /** Sur un composant focusable (ici le label), Tab et Shift+Tab transfèrent le focus. */
    @SuppressWarnings("serial")
    private void bindTabToFocusTraversal(JComponent c) {
        final KeyStroke TAB      = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);
        final KeyStroke SHIFT_TAB = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK);

        // on écrase les bindings par défaut du JTextArea
        InputMap im = c.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = c.getActionMap();

        im.put(TAB, "focusForward");
        im.put(SHIFT_TAB, "focusBackward");

        am.put("focusForward", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                c.transferFocus();
            }
        });
        am.put("focusBackward", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                c.transferFocusBackward();
            }
        });
    }


}
