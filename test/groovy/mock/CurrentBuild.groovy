package mock

import net.sf.json.JSONObject

/**
 * Mock currentBuild
 */
class CurrentBuild implements Serializable {
  String result = 'SUCCESS'
  int number = 1
  def changeSets = null
  ArrayList buildCauses

  public CurrentBuild() {
  }

  public CurrentBuild(String result) {
    this.result = result
    this.buildCauses = []
  }

  public CurrentBuild(String result, ArrayList buildCauses) {
    this.result = result
    this.buildCauses = buildCauses
  }

  public CurrentBuild(int number) {
    this.number = number
    this.buildCauses = []
  }

  public CurrentBuild(int number, boolean initializeChangeSets) {
    this.number = number
    this.buildCauses = []
    if (initializeChangeSets) {
      List<String> fileOne = [ 'README.md', ]
      def changeSetOne = [ commitId: 'SHA-1.1', author: 'writer.1', affectedFiles: fileOne ]
      this.changeSets = [ [items:changeSetOne], ]
    }
  }

  public JSONObject getBuildCauses() {
    return new JSONObject()
  }
}
