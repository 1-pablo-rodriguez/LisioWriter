package xml;


import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class node implements Cloneable{

private String nameNode = "";
private ArrayList<node> enfants = new ArrayList<node>(); 
private LinkedHashMap<String, String> attributs = new LinkedHashMap<String, String>();
private ArrayList<String> contenu = new ArrayList<String>();
private int level = 0; 
private node parent = null;
private boolean nodeClose = false;

/**
 * 
 * @author pablo rodriguez
 *
 */

	public node() {
	}
	
	public node(String nameNode) {
		setNameNode(nameNode);
	}	

	public node(String nameNode, node parent) {
		setNameNode(nameNode);
		parent.addEnfant(this);
	}

	
	public boolean isNodeClose() {
		return nodeClose;
	}

	public void setNodeClose(boolean nodeClose) {
		this.nodeClose = nodeClose;
	}

	public String getNameNode() {
		if(this.equals(null)) return "";
		return this.nameNode;
	}
	
	public void setNameNode(String nameNode) {
		this.nameNode = nameNode;
	}

	public boolean isHasAttributs() {
		if(this.attributs.size()>0) return true;
		return false;
	}
	
	public boolean isHasAttributs(String nameAttribut) {
		if(this.attributs.get(nameAttribut)==null) return false;
		return true;
	}
	
	public boolean isHasEnfant() {
		if(this.enfants.size()>0) return true;
		return false;
	}

	public int getNbrEnfants() {
		return this.enfants.size();
	}

	public ArrayList<node> getEnfants() {
		return this.enfants;
	}
	
	public node getEnfant(int index) {
		if(index<0 || index>=this.enfants.size()) return null;
		return this.enfants.get(index);
	}

	public node getEnfant(node enfant) {
		if(enfant==null) return null;
		for(int i = 0 ; i <this.enfants.size();i++) {
			if(this.enfants.get(i).equals(enfant)) return this.enfants.get(i);
		}
		return null;
	}
	
	public void addNewEnfant(String nameNewEnfant) {
		node newEnfant = new node(nameNewEnfant);
		newEnfant.setParent(this);
		this.enfants.add(newEnfant);
		this.recalculLevel();
	}

	public void addEnfant(node enfant) {
		enfant.setParent(this);
		this.enfants.add(enfant);
		this.recalculLevel();
	}
		
	public void addAllEnfants(ArrayList<node> enfants) {
		this.enfants.addAll(enfants);
		this.recalculLevel();
	}
	
	public void removeEnfant(node enfant) {
		this.enfants.remove(enfant);
	}
	
	public void removeEnfant(int index) {
		this.enfants.remove(index);
	}
	
	public void removeEnfant(String nameNode) {
		for(int i = 0 ; i < this.enfants.size() ; i++) {
			if(this.enfants.get(i).getNameNode().equals(nameNode)) {
				this.enfants.remove(this.enfants.get(i));
				break;
			}
		}
	}
	
	/**
	 * Supprime tous les nodes ayant le nom nameNode.
	 * @param nameNode
	 * @return
	 */
	public void removeAllEnfantWithThisName(String nameNode){
		 List<node> listeDelete = new ArrayList<node>();
		 boolean trouve =false;
		 for(int i =0; i < this.enfants.size();i++) {
			 if(this.enfants.get(i)!=null)if(this.enfants.get(i).getNameNode().equals(nameNode)) {
				 trouve=true;
				 listeDelete.add(this.enfants.get(i));
			 }
		 }
		 if(trouve) {
			 this.enfants.removeAll(listeDelete);
		 }
		 for(int i =0; i < this.enfants.size();i++) {
			 if(this.enfants.get(i)!=null) this.enfants.get(i).removeAllEnfantWithThisName(nameNode);
	     }
	}
	
	
	public void removeAllEnfants() {
		this.enfants.clear();
	}
	
	public void removeEnfants(ArrayList<node> enfants) {
		this.enfants.removeAll(enfants);
	}
	
	public node retourneFirstEnfant() {
		if(this.enfants.size()>0) return this.enfants.get(0);
		return null;
	}
	
	/**
	 * Retourne le premier enfant ayant le nom nameNode.<br>
	 * Sinon retourne un null.
	 * @param nameNode : le nom de l'enfant.
	 * @return
	 */
	public node retourneFirstEnfant(String nameNode) {
		if(this.nameNode.equals(nameNode)) return this;
		for (node nod : enfants) {
			node tempo = nod.retourneFirstEnfant(nameNode);
			if(tempo!=null) return tempo;
		} 
		return null;
	}
	
	public node retourneFirstEnfant(String nameNode, String nameAttribut) {
		if(this.nameNode.equals(nameNode) && this.attributs.containsKey(nameAttribut)) return this;
		for (node nod : enfants) {
			node tempo = nod.retourneFirstEnfant(nameNode, nameAttribut);
			if(tempo!=null) return tempo;
		}
		return null;
	}
	
	public node retourneFirstEnfant(String nameNode, String nameAttribut, String valueAttribut) {
		if(this.nameNode.equals(nameNode) && this.attributs.containsKey(nameAttribut)) {
			if(this.attributs.get(nameAttribut).equals(valueAttribut)) return this;
		}
		for (node nod : enfants) {
			node tempo = nod.retourneFirstEnfant(nameNode, nameAttribut,valueAttribut);
			if(tempo!=null) return tempo;
		}
		return null;
	}
	
	public node retourneFirstEnfant(String nameNode, String nameAttribut1, String valueAttribut1,String nameAttribut2, String valueAttribut2) {
		if(this.nameNode.equals(nameNode) && this.attributs.containsKey(nameAttribut1) && this.attributs.containsKey(nameAttribut2)) {
			if(this.attributs.get(nameAttribut1).equals(valueAttribut1) && this.attributs.get(nameAttribut2).equals(valueAttribut2)) return this;
		}
		for (node nod : enfants) {
			node tempo = nod.retourneFirstEnfant(nameNode, nameAttribut1,valueAttribut1,nameAttribut2,valueAttribut2);
			if(tempo!=null) return tempo;
		}
		return null;
	}
	
	public node retourneLastEnfant(String nameNode) {
		if(this.nameNode.equals(nameNode)) return this;
		for (int i = this.enfants.size()-1; i >=0 ; i--) {
			node tempo = this.enfants.get(i).retourneLastEnfant(nameNode);
			if(tempo!=null) return tempo;
		} 
		return null;
	}
	
	public node retourneNextBrother() {
		if(this.parent!=null) {
			int indexOfThis = this.parent.enfants.indexOf(this);
			indexOfThis++;
			if(indexOfThis <= this.parent.enfants.size()-1) {
				if(this.parent.enfants.get(indexOfThis)!=null) return this.parent.enfants.get(indexOfThis);
			}
		}
		return null;
	}
	
	public node retournePreviousBrother() {
		if(this.parent!=null) {
			int indexOfThis = this.parent.enfants.indexOf(this);
			indexOfThis--;
			if(indexOfThis>=0) {
				if(this.parent.enfants.get(indexOfThis)!=null) return this.parent.enfants.get(indexOfThis);
			}
		}
		return null;
	}
	
	public node retourneFirstBrother() {
		if(this.parent!=null) {
			if(this.parent.enfants.get(0)!=null) return this.parent.enfants.get(0);
		}
		return null;
	}
	
	public node retourneLastBrother() {
		if(this.parent!=null) {
			int indexLast = this.parent.enfants.size()-1;
			if(this.parent.enfants.get(indexLast)!=null) return this.parent.enfants.get(indexLast);
		}
		return null;
	}
	
	
	public node retourneLastEnfant() {
		if(this.enfants.size()>0) return this.enfants.get(this.enfants.size()-1);
		return null;
	}
	
	/**
	 * Retourne la liste des nodes ayant tous les mêmes noms.
	 * @param nameNode
	 * @return
	 */
	public ArrayList<node> retourneAllEnfants(String nameNode){
		ArrayList<node> ListeNodes = new ArrayList<node>();
		
		for(int i = 0 ; i< this.enfants.size();i++) {
			if(this.enfants.get(i)!=null) {
				if(enfants.get(i).getNameNode().equals(nameNode)) {
					ListeNodes.add(this.enfants.get(i));
				}
				ArrayList<node> B = this.enfants.get(i).retourneAllEnfants(nameNode);
				if(B.size()>0) ListeNodes.addAll(B);
				
			}
		}
		return ListeNodes;
	}
	
	public int getNbrAttributs() {
		return attributs.size();
	}
	
	public HashMap<String, String> getAttributs() {
		return attributs;
	}
	
	public String getAttributs(String nameAttribut) {
		return attributs.get(nameAttribut);
	}

	public void addAttributs(LinkedHashMap<String,String> attributs) {
		this.attributs = attributs;
	}

	public void addAttribut(String key, String value) {
		this.attributs.put(key, value);
	}
	
	public void removeAttribut(String key) {
		this.attributs.remove(key);
	}
	
	public Boolean removeAttribut(String key, String value) {
		return this.attributs.remove(key,value);
	}
	
	public void removeAllAttributs() {
		this.attributs.clear();
	}

	public node getParent() {
		return parent;
	}
	
	public node getFirstParentNotClose() {
		if(!this.parent.isNodeClose()) return this;
		if(this.parent==null) return null;
		return this.getFirstParentNotClose();
	}

	public void setParent(node parent) {
		this.parent = parent;
	}
	
	public String getAllNameParents() {
		 if(this.parent!=null) {
			 return this.parent.getAllNameParents() + "/" + this.parent.getNameNode();
		 }
		return "";
	}
	
	public node retourneFirstParent(String nameNode) {
		if(this.parent==null) return null;
		if(this.parent.getNameNode().equals(nameNode)) return this.parent;
		return this.retourneFirstParent(nameNode);
	}
	
	/**
	 * Retourne Le premier parent ayant l'attribut demandé.<br>
	 * Si ne trouve pas, alors retourne un node null.<br>
	 * @param nameAttribut
	 * @return
	 */
	public node retourneParentAyantLAttribut(String nameAttribut) {
		if(this.getAttributs().get(nameAttribut)!=null) {
			return this;
		}else {
			if(this.parent!=null) {
				node nod = this.parent.retourneParentAyantLAttribut(nameAttribut);
				if(nod!=null) return nod;
			}
		}
		return null;
	}
	
	public node retourneRoot() {
		if(this.parent!=null) this.parent.retourneRoot();
		 return this;
	}
	
	public void recalculParent() {
		for(node child : enfants) {
			child.setParent(this);
			child.recalculParent();
		}
	}

	public int getLevel() {
		return this.level;
	}

	public void setLevel(int level) {
		this.level = level;
	}
	
	public void incrementeLevel() {
		this.level++;
	}
	
	public void decrementeLevel() {
		this.level--;
	}
	
	public void recalculLevel() {
		if(this.parent!=null) {
			this.level=this.parent.level+1;
		}else {
			this.level=0;
		}
		for(node child : this.enfants) {
			child.recalculLevel();
		}
	}
	
	public ArrayList<node> retourneAllNodesLevelEquals(int levelNode) {
		ArrayList<node> allNodesLevelEquals = new ArrayList<node>();
		if(this.level==levelNode) {
			for(int i=0;i<this.parent.enfants.size();i++) {
				allNodesLevelEquals.add(this.parent.getEnfant(i));
			}
		}else {
			for(int i=0;i<this.enfants.size();i++) {
				allNodesLevelEquals.addAll(retourneAllNodesLevelEquals(levelNode));
			}
		}
		return allNodesLevelEquals;
	}

	public ArrayList<String> getContenu() {
		return contenu;
	}
	
	public String getContenu(int index) {
		return this.contenu.get(index);
	}

	public void addContenu(String texte) {
		this.contenu.add(texte);
	}
	
	public void removeAllContenu() {
		this.contenu.clear();
	}
	
	public void removeContenu(int index) {
		this.contenu.remove(index);
	}
	
	public void modifieContenu(int index, String newContenu) {
		if(this.contenu.get(index)!=null) {
			this.contenu.add(index, newContenu);
			this.contenu.remove(index+1);
		}
	}
	
	public String getContenuAvecTousLesContenusDesEnfants() {
		String contenuavectouslescontenudesenfants = "";
		for(int i = 0 ; i < this.contenu.size();i++) {
			contenuavectouslescontenudesenfants = contenuavectouslescontenudesenfants + this.contenu.get(i);
			for(int j = 0 ; j < this.enfants.size();j++) {
				contenuavectouslescontenudesenfants = contenuavectouslescontenudesenfants + this.enfants.get(j).getContenuAvecTousLesContenusDesEnfants();
			}
		}
		return contenuavectouslescontenudesenfants;
	}
	
	public Boolean contenuContain(String texte) {
		if (getContenuAvecTousLesContenusDesEnfants().contains(texte)) return true;
		return false;
	}
	
	
	public Boolean isVide() {
		return this.attributs.size()==0
				&& this.contenu.size()==0
				&& this.enfants.size()==0 
				&& this.nameNode.isBlank();
	}
	
	
	public boolean moveTo(node nodeParent) {
		if(nodeParent!=null) {
			this.parent.getEnfants().remove(this);
			this.parent = nodeParent;
			this.level = nodeParent.getLevel()+1;
			nodeParent.getEnfants().add(this);
			return true;
		}
		return false;
	}
	

	public boolean moveToFirstChild() {
		if(this.parent!=null) {
			int index = this.parent.enfants.indexOf(this);
			if(index > 0 && index < this.parent.enfants.size()) {
				this.parent.enfants.add(0, this);
				this.parent.enfants.remove(index+1);
				return true;
			}
		}
		return false;
	}
	
	public boolean moveToLastChild() {
		if(this.parent!=null) {
			int index = this.parent.enfants.indexOf(this);
			if(index >= 0  && index < this.parent.enfants.size()-1) {
				this.parent.enfants.remove(index);
				this.parent.enfants.add(this.parent.enfants.size()-1, this);
				return true;
			}
		}
		return false;
	}
	
	
	public boolean moveUp() {
		if(this.parent!=null) {
			int index = this.parent.enfants.indexOf(this);
			if(index > 0 && index < this.parent.enfants.size()) {
				this.parent.enfants.add(index-1, this);
				this.parent.enfants.remove(index+1);
				return true;
			}
		}
		return false;
	}
	
	public boolean moveDown() {
		if(this.parent!=null) {
			int index = this.parent.enfants.indexOf(this);
			if(index >= 0  && index < this.parent.enfants.size()-1) {
				this.parent.enfants.remove(index);
				this.parent.enfants.add(index+1, this);
				return true;
			}
		}
		return false;
	}
	
	public boolean moveLeft() {
		if(this.parent!=null) {
			if(this.parent.parent!=null) {
				int index = this.parent.enfants.indexOf(this);
				this.parent.enfants.remove(index);
				this.parent.parent.enfants.add(this);
				this.setParent(this.parent.parent);
				this.level--;
				return true;
			}
		}
		return false;
	}
	
	public boolean moveRight() {
		if(this.parent!=null) {
			if(this.parent!=null) {
				int index = this.parent.enfants.indexOf(this);
				if(index-1>0) {
					this.parent.enfants.remove(index);
					this.parent.getEnfant(index-1).getEnfants().add(this);
					this.setParent(this.parent.getEnfant(index-1));
					this.level++;
					return true;
				}	
			}
		}
		return false;
	}
	
	

	@Override
	public int hashCode() {
		int a = nameNode.hashCode();
		int b = 0;
		int c = 0 ;
//		int h = 0;
		if(parent!=null) {
			c = this.parent.getAllNameParents().hashCode();
//			h = this.parent.enfants.indexOf(this);
		}
		int d = level;
		int e = attributs.hashCode();
		int f = contenu.hashCode();
		int  g = 0;
		String hashG = "";
		for(node child : enfants) {
			hashG = hashG + String.valueOf(child.hashCode());
		}
		if(!hashG.isEmpty()) g = hashG.hashCode();
		
		
		
		
		String H = (String.valueOf(a)+String.valueOf(b)+String.valueOf(c)
		+String.valueOf(d)+String.valueOf(e)+String.valueOf(f)+String.valueOf(g));
		
		return H.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		node other = (node) obj;
		return Objects.equals(attributs, other.attributs)
				&& Objects.equals(contenu, other.contenu)
				&& Objects.equals(enfants, other.enfants) && level == other.level
				&& Objects.equals(nameNode, other.nameNode) && Objects.equals(parent, other.parent);
	}


	@SuppressWarnings("unchecked")
	@Override
	public node clone() throws CloneNotSupportedException {
		node b = (node) super.clone();
		b.enfants = (ArrayList<node>) this.enfants.clone();
		b.setParent(null);
		
		if(this.enfants.size()>0) {
			b.enfants.clear();
			for(int i = 0 ; i < this.enfants.size();i++) {
				b.enfants.add(i, this.enfants.get(i).clone());
			}
		}

		b.attributs = (LinkedHashMap<String, String>) this.attributs.clone();
		b.contenu = (ArrayList<String>) this.contenu.clone();
		
		b.recalculParent();
		b.recalculLevel();

		return b;
	}

	public StringBuilder ecritureXML() {
		
		StringBuilder sb = new StringBuilder();
		sb.append("<"  + this.nameNode  );
		for (Entry<String, String> entry : this.attributs.entrySet()) {
			sb.append(" " + entry.getKey() + "=\"" + entry.getValue()+"\"");
		}

		sb.append(">");

		int indexContenu = 0;
		int indexMaxContenu = this.contenu.size();
		if((indexMaxContenu-indexContenu)>0) {
			if(this.contenu.size()>indexContenu) {
				sb.append(this.contenu.get(indexContenu));
				indexContenu++;
			}
		}
		
		if(this.enfants.size()>0) {
			for(int i = 0 ; i < this.enfants.size(); i++) {
				sb.append(this.enfants.get(i).ecritureXML());
				if((indexMaxContenu-indexContenu)>0) {
					if(this.contenu.size()>indexContenu) {
						sb.append(this.contenu.get(indexContenu));
						indexContenu++;
					}
				}
			}
			
			//s'il reste du contenu suite à la suppression d'un node
			if((indexMaxContenu-indexContenu)>0) {
				for(int i = indexContenu ; i < indexMaxContenu; i++) {
					sb.append(this.contenu.get(i));
				}
			}
			
			sb.append("</" + this.nameNode + ">");
			
		}else {
			//s'il reste du contenu suite à la suppression d'un node
			if((indexMaxContenu-indexContenu)>0) {
				for(int i = indexContenu ; i < indexMaxContenu; i++) {
					sb.append(this.getContenu().get(i));
				}
			}
			sb.append("</" + this.nameNode + ">");	
		}
		return sb;
	}
	
	/**
	 *  overwrite toString pour la JTree
	 */
	@Override
	public String toString()
	  {
		String retourneIdentifiantNode =this.nameNode;
		
		if(retourneIdentifiantNode.equals("feuille")) {
			return retourneIdentifiantNode = retourneIdentifiantNode + " * " + this.attributs.get("nomFeuille");
		}
		
		if(retourneIdentifiantNode.equals("colonne")) {
			return retourneIdentifiantNode = retourneIdentifiantNode + " * " + this.attributs.get("RefColDansClasseur");
		}
		
		if(retourneIdentifiantNode.equals("ligne")) {
			retourneIdentifiantNode = retourneIdentifiantNode + " * " + this.attributs.get("RefLigne");
		}
		
		if(retourneIdentifiantNode.equals("cellule")) {
			retourneIdentifiantNode = retourneIdentifiantNode + " * " + this.attributs.get("RefColDansClasseur") + this.attributs.get("RefLigDansClasseur");
		}
		

		if(retourneIdentifiantNode.equals("meta:user-defined")) {
			if(this.attributs.get("meta:name").contains("‽")) return retourneIdentifiantNode = retourneIdentifiantNode + " * " + this.attributs.get("meta:name").substring(0,this.attributs.get("meta:name").lastIndexOf("‽"));	
			retourneIdentifiantNode = retourneIdentifiantNode + " * " + this.attributs.get("meta:name");
		}
		
		if(retourneIdentifiantNode.equals("graphic")) {
			if(this.attributs.get("nom")==null) retourneIdentifiantNode = retourneIdentifiantNode + " * " + this.attributs.get("nomObjet");
			if(this.attributs.get("nom")!=null) retourneIdentifiantNode = retourneIdentifiantNode + " * " + this.attributs.get("nom");
		}

		return retourneIdentifiantNode;
	  }
	


	/**
	 * Insère l'attribut evaluer=true à ce node mais aussi aux nodes endfants.<br>
	 * Si ce node est de level== alors insère aussi l'attribut addMenu=true.
	 */
	public void evaluerAllChildTrue() {
		this.attributs.put("evaluer", "true");
		if(this.level==1) attributs.put("addmenu", "true");
		for(int i = 0 ; i < this.enfants.size();i++) {
			if(this.enfants.get(i)!=null) this.enfants.get(i).evaluerAllChildTrue();
		}
	}
	
	/**
	 * Insère l'attribut evaluer=false dans ce node uniquement.<br>
	 * Si ce node est level==1 alors l'attribu addMenu=false.
	 */
	public void evaluerFalse() {
		attributs.put("evaluer", "false");
		if(this.level==1) attributs.put("addmenu", "false");
	}
	
	
	/**
	 * Insère l'attribut evaluer=true dans ce node uniquement.<br>
	 * Et celui de tous les nodes parents jusqu'à la racine.<br>
	 * Si un node parent est de level==1 alors insère aussi l'attribut addMenu=true.
	 * 
	 */
	public void evaluerTrue() {
		this.attributs.put("evaluer", "true");
		if(this.level==1) this.attributs.put("addmenu", "true");
		if(parent!=null) {
			this.parent.evaluerTrue();
		}
	}
	
	/**
	 * Insère l'attribut saut.
	 * @param value : valeur logique du saut.
	 */
	public void saut(Boolean value) {
		attributs.put("saut", String.valueOf(value));
	}
	
	/**
	 * Insère l'attribut titre avec le texte.<br>
	 * Supprimer tous les attributs titre1, titre2 et titre3.
	 * @param Text : Le texte à placer.
	 */
	public void titre(String Text) {
		attributs.put("titre", Text);
		attributs.remove("titre1");
		attributs.remove("titre2");
		attributs.remove("titre3");
	}
	
	/**
	 * Insère l'attribut titre1 avec le texte.<br>
	 * Supprimer tous les attributs titre, titre2 et titre3.
	 * @param Text : Le texte à placer.
	 */
	public void titre1(String Text) {
		attributs.remove("titre");
		attributs.put("titre1", Text);
		attributs.remove("titre2");
		attributs.remove("titre3");
	}
	
	/**
	 * Insère l'attribut titre2 avec le texte.<br>
	 * Supprimer tous les attributs titre, titre1 et titre3.
	 * @param Text : Le texte à placer.
	 */
	public void titre2(String Text) {
		attributs.remove("titre");
		attributs.remove("titre1");
		attributs.put("titre2", Text);
		attributs.remove("titre3");
	}
	
	/**
	 * Insère l'attribut titre3 avec le texte.<br>
	 * Supprimer tous les attributs titre, titre1 et titre2.
	 * @param Text : Le texte à placer.
	 */
	public void titre3(String Text) {
		attributs.remove("titre");
		attributs.remove("titre1");
		attributs.remove("titre2");
		attributs.put("titre3", Text);
	}

	/**
	 * Ecriture d'un node dans un fichier au format XML.<br>
	 * 
	 * @param nod Le node à écrire dans un fichier.
	 * @param filename Le nom du fichier.
	 * @param pathDestination Le répertoire de destination qui sera inclus dans le répertoire courant de l'application.
	 * @param fourniDestination Le répertoire courant de l'application.
	 * @throws IOException Exception Input Output
	 * @return Erreur True ou False
	 */
		public boolean saveNodeEnXML(String filenameWithExtension, String pathDestination) {
		if(!filenameWithExtension.contains(".xml")) {
			filenameWithExtension = filenameWithExtension.substring(0,filenameWithExtension.lastIndexOf(".xml"));
		}
			
		Path outputFilePath = Paths.get(pathDestination +"/"+ filenameWithExtension + ".xml");
		
		if(Files.isWritable(outputFilePath)){
			try {
				BufferedWriter  fichier = Files.newBufferedWriter(outputFilePath, StandardCharsets.UTF_8);
				fichier.write(this.ecritureXML().toString());
				fichier.close();
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}else {
			return false;
		}
		return true;
	}
	
		/**
		 * Insère l'attribut addmenu avec la valeur logique.
		 * @param value : valeur logique.
		 */
		public void addMenu(Boolean value) {
			attributs.put("addmenu", String.valueOf(value));
		}
		
		/**
		 * Insère le poids avec sa valeur.
		 * @param P : La valeur.
		 */
		public void poids(Double value ) {
			attributs.put("poids", String.valueOf(value));
		}
		
		/**
		 * Supprime un attribut du node
		 * @param key
		 */
		public void supprimeAttribut(String key) {
			try {
				if(attributs.get(key)!=null) {
					attributs.remove(key);
				}
			} catch (Exception e) {
				System.out.println(e.toString());
			}
		}
		
		/**
		 * Insère l'attribut evaluer=false dans toute la branche de la racine à la feuille.
		 */
		public void evaluerAllChildFalse() {
			evaluerFalse();
			for(int i = 0 ; i < enfants.size();i++) {
				if(enfants.get(i)!=null) enfants.get(i).evaluerAllChildFalse();
			}
		}
		
		
		/**
		 * Retourne la liste des enfants qui porte le nom "<b>nameNode</b>".<br>
		 * Et qui contient un attribut nommé "<b>nameAttribut</b>".<br>
		 * Et dont la valeur de cet attribut est "<b>valueAttribut</b>".<br>
		 * Sinon retourne un node null.<br> 
		 * @param nameNode
		 * @return
		 */
		public ArrayList<node> retourneAllEnfants(String nameNode, String nameAttribut, String valueAttribut){
			ArrayList<node> ListeNodes = new ArrayList<node>();
			
			if(this.nameNode.equals(nameNode)) {
				if(this.attributs.get(nameAttribut)!=null) {
					if(this.attributs.get(nameAttribut).equals(valueAttribut)) {
						ListeNodes.add(this);
					}
				}
			}
			for(int i = 0 ; i< this.enfants.size();i++) {
				if(this.enfants.get(i)!=null) {
//					nodeAC nod = Nodes.get(i).retourneFirstEnfantsByName(nameNode, nameAttribut, valueAttribut);
					ArrayList<node> B = this.enfants.get(i).retourneAllEnfants(nameNode, nameAttribut, valueAttribut);
					if(B.size()>0) ListeNodes.addAll(B);
				}
			}
			return ListeNodes;
		}
		
	
		/**
		 * Supprime tous les nodes contenant l'attribut evaluer=false<br>
		 * ou ne contenant pas l'attribut evaluer.
		 */
		public void supprimeTousLesNodesEvaluerFalseOuNull() {
			List<node> listeDelete = new ArrayList<node>();
			 boolean trouve =false;
			 for(int i =0; i < this.enfants.size();i++) {
				 if(!this.enfants.get(i).getNameNode().equals("setting")) {
					 if(this.enfants.get(i).getAttributs().get("evaluer")!=null) {
						 if(!this.enfants.get(i).getAttributs().get("evaluer").equalsIgnoreCase("true")) {
							 listeDelete.add(this.enfants.get(i));
							 trouve=true;
						 }
					 }else {
						 listeDelete.add(this.enfants.get(i));
						 trouve=true;
					 }
				 }
			}
			 if(trouve)  this.enfants.removeAll(listeDelete);
			 for(int i =0; i < this.enfants.size();i++) {
				 if(!this.enfants.get(i).getAllNameParents().contains("setting")) {
					 this.enfants.get(i).supprimeTousLesNodesEvaluerFalseOuNull();
				 }
		     }
		}
		
		/**
		  * 
		  * @param nameNode
		  * @return
		  */
		 public boolean containChildByName(String nameNode) {
			 if(this.nameNode.equals(nameNode)) return true;
			 for(int i = 0 ; i < this.enfants.size();i++) {
				 if(this.enfants.get(i)!=null)if(this.enfants.get(i).containChildByName(nameNode)) return true;
			 }
			 return false;
		 }
		 
		 
			/**
			 * Cette méthode permet de lire uniquement les nodes evaluations.
			 * @param code
			 * @return
			 */
			public node allFirstNodesEvaluationFichierOnly(String code) {
				node evaluations = new node();
				evaluations.setNameNode("evaluationsCalc");
				Pattern p = Pattern.compile("<\\bevaluation\\b.*?<\\broot\\b.*?</root></evaluation>");
				Matcher m = p.matcher(code.trim());
				StringBuilder LesEvals = new StringBuilder();
				while(m.find()) {
					LesEvals.append(code.substring(m.start(), m.end()));
				}
				new transformeXLMtoNode(LesEvals.toString(), false, null); //.replaceAll(">/{1,}<", "><")
				node nodRetourne =  transformeXLMtoNode.getNodeRoot(); //new node(LesEvals.toString().replaceAll(">/{1,}<", "><")); //Le node A est nécessaire.
				if(nodRetourne.getNameNode().equals("root")) {
					evaluations.getEnfants().addAll(nodRetourne.getEnfants());
				}else {
					evaluations.getEnfants().add(nodRetourne);
				}
				 //nécessaire pour la reconnaissance du node de donner le nom evaluations avec un s
				return evaluations;
			}
		 
			/**
			 * Charge l'évaluation avec l'index
			 * @param index
			 * @param code
			 * @return
			 */
			public node chargeNodeEvaluationIndex(Integer index, String code) {
				node nodRetourne = null;
				Pattern p = Pattern.compile("<\\bevaluation\\b.*?</\\bevaluation\\b>");
				Matcher m = p.matcher(code.trim());
				StringBuilder LesEvals = new StringBuilder();
				int i = 0;
				boolean trouve=false;
				while(m.find()&&i<=index&&!trouve) {
					if(i==index) {
						LesEvals.append(code.substring(m.start(), m.end()));
						trouve=true;
					}
					i++;
				}
				new transformeXLMtoNode(LesEvals.toString(), false, null);
				nodRetourne = transformeXLMtoNode.getNodeRoot().getEnfant(0);
				return nodRetourne;
			}
		
			/**
			 * Recherche le node evaluation par date.
			 * @param code
			 * @param dateString
			 * @return
			 */
			public node chageNodesEvaluationByDate(String code,String dateString) {
				node nodRetourne = null;
				Pattern p = Pattern.compile("<\\bevaluation\\b.*?</\\bevaluation\\b>");
				Matcher m = p.matcher(code.trim());
				boolean trouve=false;
				while(m.find()&&!trouve) {
					String NodeString = code.substring(m.start(), m.end());
					if(NodeString.contains(dateString)) {
						trouve=true;
						nodRetourne = new node("<!-- A -->"+NodeString.toString().replaceAll(">/{1,}<", "><")); //Le node A est nécessaire.
					}
				}
				return nodRetourne;
			}
			

}
	
	

	 

	
	
