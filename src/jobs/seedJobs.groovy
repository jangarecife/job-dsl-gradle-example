// JobDSL script for generating new Jenkins jobs using the JobDSL pluging.
// See https://github.com/jenkinsci/job-dsl-plugin/wiki
//
// TO-DO: rename file to `seed.groovy` to make it obvious that this is not a pipeline,
//        but a script to generate pipelines/jobs
//
// TO-DO: Run this script on push-to-git?
//      "It’s a good idea to set up your ORIG_seed job to run when your jobs SCM repo is updated, obviously, so add that SCM trigger."
//      - https://marcesher.com/2016/06/09/jenkins-as-code-job-dsl/

import java.security.MessageDigest

def rUserId = 'code_test'
def cId = 'svcappjenkinsgit'

def secretString = 'daRocks.smart'

//Job support mailing list
def supportMailList = 'Rasmus.Moseholm@skat.dk Assaad.Al-Kassem@skat.dk Jesper.Petersen@skat.dk'

def integrationBranch = ''
def gitRepoServer = "git@sktpprdgit01.ccta.dk"
def workflows = readCSV()
def package_type = ""

//For hver model vi har i Jenkins
for (def workflow : workflows) {

//    folder("model-dev-jobs") {
//        authorization {
//            permission('hudson.model.Item.Read', 'AP_JenkinsCI_Prod_Analytiker')
//            permission('hudson.model.Item.Discover', 'AP_JenkinsCI_Prod_Analytiker')
//        }
//    }
//
    folder("model-master-jobs") {
        authorization {
            permission('hudson.model.Item.Read', 'AP_JenkinsCI_Prod_Analytiker')
            permission('hudson.model.Item.Discover', 'AP_JenkinsCI_Prod_Analytiker')
        }
    }
//
//    folder("tools-master-jobs") {
//        authorization {
//            permission('hudson.model.Item.Read', 'AP_JenkinsCI_Prod_Analytiker')
//            permission('hudson.model.Item.Discover', 'AP_JenkinsCI_Prod_Analytiker')
//        }
//    }
//
//    folder("train-jobs")
    folder("prod-jobs-batch")
    folder("prod-jobs-deploy")
//    folder("admin-jobs")

    if(workflow.Organisation ==~ /.*CoDe/) {
        package_type = "model"
    } else if (workflow.Organisation ==~ /.*tools/) {
        package_type = "tools"
    } else {
        package_type = "model"
    }

    if(package_type == "model") {
        // fjernet indtil dev branch pipeline er lavet
        // pipelineJob("${package_type}" + "-dev-jobs/"+workflow.Model+"_workflow") {
        //        setupWorkflowJob(it, workflow, gitRepoServer, cId)
        //  }

        pipelineJob("${package_type}" + "-master-jobs/"+workflow.Model+"_workflow") {
            setupWorkflowJob(it, workflow, gitRepoServer, cId)
        }
    } else if(package_type == "tools") {
        pipelineJob("${package_type}" + "-master-jobs/"+workflow.Model+"_workflow") {
            setupWorkflowJob(it, workflow, gitRepoServer, cId)
        }
    }

    if(workflow.Batch.toBoolean()) {
        pipelineJob("prod-jobs-batch/"+workflow.Model+"_batch") {

            logRotator {
                artifactNumToKeep(30)
            }

            label("prod&&R")

            //If timing is specified
            if(workflow.Timing?.trim()) {
                triggers {
                    cron(workflow.Timing)
                }
            }

            authenticationToken(MessageDigest.getInstance("MD5").digest((secretString + workflow.Model).bytes).encodeHex().toString().substring(12))

            parameters {
                stringParam('model_name', "${workflow.Model}", 'Model name')
                stringParam('model_args', '', 'Additional model batch argmuents')
            }

            definition {
                cps {
  //todo                  script(readFileFromWorkspace('Jenkins/JobDSL/batch.groovy'))
                    sandbox(false)
                }
            }

            publishers {
                extendedEmail {
                    recipientList(supportMailList)
                    defaultSubject("Batch job fejlede: ${workflow.Model}")
                    defaultContent('Venligst undersøg dette fejlede batch job')
                    contentType('text/html')
                    triggers {
                        failure {
                            subject("Batch job fejlede: ${workflow.Model}")
                            content('Venligst unders&oslash;g dette fejlede batch job')
                            recipientList(supportMailList)
                            attachBuildLog(true)
                        }
                    }
                }
            }

        }
    }

    pipelineJob("prod-jobs-deploy/"+workflow.Model+"_deploy") {

        parameters {
            stringParam('packageName', "${workflow.Model}", 'Package name')
            stringParam('isopencpu', "${workflow.OpenCPU}", 'OpenCPU enabled')
            stringParam('isbatch', "${workflow.Batch}", 'Batch enabled')
            stringParam('packageVersion', 'current', 'Version to install')
            stringParam('model_db_credentials', "${workflow.Credentials}", 'ID på credentials fra Jenkins credentials store der skal anvendes ved kørsel af modellen')
        }

        definition {
            cps {
//todo                script(readFileFromWorkspace('Jenkins/JobDSL/workflow_prod_deploy.groovy'))
                sandbox(false)
            }
        }
    }
}

//buildMonitorView("BatchMonitor") {
//    description("View som overvåger batch jobs i produktion")
//    recurse(true)
//    jobs {
//        regex(".*_batch|.*_admin")
//    }
//}

//buildMonitorView("BatchMonitor_Fejlede") {
//    description("View som overvåger batch jobs i produktion")
//    recurse(true)
//    jobs {
//        regex(".*[_batch]")
//    }
//
//    jobFilters {
//        status {
//            matchType(javaposse.jobdsl.dsl.views.jobfilter.MatchType.EXCLUDE_UNMATCHED)
//            status(javaposse.jobdsl.dsl.views.jobfilter.Status.FAILED, javaposse.jobdsl.dsl.views.jobfilter.Status.UNSTABLE)
//        }
//    }
//}

//buildMonitorView("BatchMonitor_SenestFejlede") {
//    description("View som overvåger batch jobs i produktion")
//    recurse(true)
//    jobs {
//        regex(".*[_batch]|.*_admin")
//    }
//
//    jobFilters {
//        buildTrend {
//            matchType(MatchType.EXCLUDE_UNMATCHED)
//            buildCountType(BuildCountType.AT_LEAST_ONE)
//            amountType(AmountType.DAYS)
//            amount(7)
//            status(BuildStatusType.FAILED)
//        }
//    }
//
//}

def configureGit(def context, def repoUrl, def creds = '') {
    context.with {
        scm {
            git {
                remote {
                    name('origin')
                    url(repoUrl)
                    credentials(creds)
                }
                branch('master')
            }
        }
    }
}

def readCSV(def modelFile = "src/main/resources/jenkins_models.csv", def seperator = ";") {
    models = []
    keys = [:]
    def csv = readFileFromWorkspace(modelFile)
    def lineNumber = 0;
    csv.eachLine { line ->
        def splitLine = line.split(seperator).toList()
        if(lineNumber == 0) {
            keys = recordKey(splitLine, seperator)
        } else {
            //Get the
            data = [:]
            splitLine.eachWithIndex { v, i ->
                data[(keys[i])]=v
            }
            models << data
        }
        lineNumber++
    }
    return models
}

def recordKey(def splitLine, def seperator) {
    keyList = splitLine.collectEntries { col ->
        [((splitLine.indexOf(col))) : col]
    }
    return keyList
}

def createPermissions(def permissionString, def context) {
    def permissions = permissionString.split(" ")
    context.with {
        authorization {
            permissions.each { perm ->
                permission('hudson.model.Item.Build', perm)
                permission('hudson.model.Item.Read', perm)
                permission('hudson.model.Item.Cancel', perm)
                permission('hudson.model.Item.Discover', perm)
                permission('hudson.model.Run.Replay', perm)
            }
        }
    }
}


def setupWorkflowJob(def context, def workflow, def gitRepoServer, def cId) {

    context.with {
        //Workflow

        logRotator {
            artifactNumToKeep(30)
        }

        triggers {
            scm('* * * * *')
        }

        createPermissions("$workflow.Brugere", it)

        parameters {
            stringParam('modelUrl', "${gitRepoServer}:${workflow.Organisation}/${workflow.Model}.git", 'Model repository')
            credentialsParam('modelCredentials') {
                defaultValue(cId)
            }
        }

        definition {
            cps {
//todo                script(readFileFromWorkspace('Jenkins/JobDSL/workflow.groovy'))
                sandbox(false)
            }
        }

    }

}

