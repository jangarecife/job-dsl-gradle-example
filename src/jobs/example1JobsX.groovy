String basePath = 'example1'
String repo = 'jangarecife/job-dsl-gradle-example'

folder(basePath) {
    description 'This example shows basic folder/job creation.'
}

job("$basePath/grails example build") {
    scm {
        github repo
    }
    triggers {
        scm 'H/5 * * * *'
    }
    steps {
        printf('Echo w. printf')
//        grails {
//            useWrapper true
//            targets(['test-app', 'war'])
//        }
    }
}

job("$basePath/grails example deploy") {
    parameters {
        stringParam 'host'
    }
//    steps {
//        shell 'scp war file; restart...'
//    }
}