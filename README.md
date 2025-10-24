# blindWriter

![Language: French](https://img.shields.io/badge/lang-French-blue?style=flat-square&logo=google-translate)
![Platform: Windows](https://img.shields.io/badge/platform-Windows-lightgrey?style=flat-square&logo=windows)

**blindWriter** est un logiciel de traitement de texte conçu spécialement pour les personnes **non-voyantes ou malvoyantes**.
Son interface est pensée pour être utilisée **entièrement au clavier** et reste compatible avec les lecteurs d’écran tels que **NVDA** ou **JAWS**.

La mise en forme du texte (titres, listes, emphase, etc.) n’est pas appliquée de manière invisible, mais indiquée directement par de petits codes lisibles inspirés du **Markdown**.
Ces codes sont intégrés directement dans le texte. Ils sont ensuite lus par le lecteur d’écran et transmis à la barre braille, ce qui permet à l’utilisateur de savoir exactement quelle mise en forme est appliquée.

---

## 📌 Exemple de code dans blindWriter

- **#P. Mon titre** : Indique mon titre pincipale de niveau hiérarchique corps de texte.
- **#S. Sous-titre** : Indique un sous-titre  de niveau hiérarchique corps de texte.
- **#1. Un titre** : Indique un titre de chapitre de niveau hiérachique 1 (équivalent à **Titre 1** sous Writer , Word, ou bien la balise **\<H1\>** dans une page web).
- **etc.**

- **\*\*mot en gras\*\*** : Indique des mots en gras.
- **\*^mot en italique^\*** : Indique des mots en italique.
- **__mots soulignés__** : Indique des mots soulignés.
- **_\*mots gras soulignés\*_** : Indique des mots gras soulignés.
- **etc.**

- **@saut de page** indique un saut de page.
- **@(note de bas de page)** indique une note de bas de page.
- °°**Erreur ortographique ou grammaticale**.
- **etc.**

---

## 🎯 Objectifs

Permettre à toute personne non-voyante ou déficiente visuelle de rédiger, lire et mettre en forme des documents de manière fluide, rapide et indépendante, grâce à une interface accessible et simple d’utilisation.

Le logiciel s’adresse en priorité :

- aux **étudiants** non-voyants ou malvoyants engagés dans des **cursus post-bac**,
- ainsi qu’aux **adultes** déjà expérimentés dans l’usage des **traitements de texte**.

L’objectif est de fournir un outil qui favorise l’autonomie numérique, tout en restant proche des standards bureautiques connus (Word, Writer, Markdown), afin de faciliter l’apprentissage et **l’intégration dans les environnements éducatifs ou professionnels**.

---

## ✨ Fonctionnalités principales

- **Interface 100 % clavier** : navigation par raccourcis, annonces contextuelles.  
- **Compatibilité NVDA / JAWS** : lecture vocale automatique des actions et des éléments de texte.  
- **Ouverture et exportation** :
  - Fichiers **.docx** (Microsoft Word)
  - Fichiers **.odt** (LibreOffice Writer)
  - Fichiers **.txt** (texte brut)
  - Fichiers  **.html** (page web)
  - Exportation en **PDF**
- **Vérification orthographique intégrée** (basée sur *LanguageTool*).  
- **Surlignage et tag preffix °° des erreurs et suggestion des corrections**.  
- **Mode documentation blindWriter** : **ALT+A** permet de basculer sur la documentation et **ALT+B** permet de basculer sur son fichier.
- **Navigateur et navigation** : Fenêtre simple pour la navigation par les titres.
- **Marque page** : intégration de marque page et de note.
- **Système de mise à jour automatique** : télécharge et installe la dernière version en arrière-plan.  
- **Accessibilité vocale (SAPI)** : le programme parle pendant l’installation et les mises à jour.  

---

## 🧩 Installation
⚠️ **Actuellement, blindWriter est uniquement disponible pour Windows.**  
Une version multiplateforme (Linux, macOS) pourra être envisagée plus tard.

1. Téléchargez la dernière version depuis la page [**Releases**](https://github.com/1-pablo-rodriguez/blindWriter/releases).  
2. Exécutez le fichier `Installation_blindWriter_x.x.x.exe`.  
3. Suivez les instructions vocales.  
4. Un raccourci sera créé sur le bureau et dans le menu Démarrer.

---

## 🗣️ Quelques utilisations pratiques

- **F1** : Annonce d’informations sur la fenêtre en cours ou sur l’ensemble du document.  
- **F2** : Lecture du titre du paragraphe en cours et annonce du titre suivant.  
- **F3 / Maj + F3** : Navigation rapide vers le titre suivant ou précédent.  
- **ALT+A** : Ouvrir la documentation intégrée.  
- **F6** : Ouvrir le navigateur de titres.  
- **Ctrl + F7** : Lancer la vérification orthographique et grammaticale de tout le document.  
- **Ctrl + F2** : Ajouter un marque-page annoté.  
- *etc.*  

Toutes les fonctions du logiciel sont accessibles **entièrement au clavier, sans souris**.  
Les menus sont conçus pour être **vocalisés** et **compatibles avec les barres braille**, afin de garantir une accessibilité complète.

---

## 🧱 Technologies utilisées

- **Java 21** (Swing)  
- **LanguageTool** pour la correction grammaticale et orthographique  
- **Apache POI** pour la lecture/écriture de fichiers Word  
- **ODFDOM** pour le format LibreOffice  
- **iText** pour l’exportation PDF  
- **Inno Setup** pour le programme d’installation et la mise à jour  
- **SAPI** (Microsoft Speech API) pour la synthèse vocale pendant l’installation

---

## 🔄 Mise à jour automatique

blindWriter vérifie périodiquement les mises à jour via un fichier `updates.json` hébergé sur GitHub :  
> `https://raw.githubusercontent.com/1-pablo-rodriguez/blindWriter/main/updates.json`

Les nouvelles versions sont téléchargées et installées automatiquement, silencieusement ou avec fenêtre visible selon le mode choisi.

---

## 💬 Contribution

Les contributions sont les bienvenues !  
Vous pouvez :
- Signaler des bugs dans l’onglet **Issues**
- Proposer des améliorations d’accessibilité ou de compatibilité
- Soumettre des pull requests

---

## 👤 Auteur

Développé par **Pablo Rodriguez**, pour favoriser l’autonomie numérique des personnes non-voyantes et malvoyantes.

---

## 🪪 Licence

Ce logiciel est distribué sous licence **GNU GLP3.0**.  
Vous pouvez l’utiliser, le modifier et le redistribuer librement à condition de conserver la mention du copyright.

---

> 💡 *blindWriter est avant tout un outil d’inclusion numérique, conçu pour que la rédaction, la lecture et la révision de documents soient accessibles à tous.*
























