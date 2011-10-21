API duplication between BuildContext and BuildContextManager. It was
necessary to allow implicit BuildContext propagation from the code where
BuildContext was setup to the code where it was used. Still, I believe
this duplication is both confusing to the users of the API and adds at
least some complexity to the implementation. BuildContext injections
makes this all unnecessary, so now redundant BuildContextManager API
methods and corresponding implementation can be removed.

DefaultBuildContextManager.buildStates cache does not detect state file
changes on filesystem by another process, a likely scenario when m2e and
maven are used on the same project. No way to flush the cache either
from m2e. We need to either write more code to make cache more robust or
remove it altogether.

What is the usecase for BuildContext.outputDirectory?
AbstractModelloGeneratorMojo is the only real client, where it is not
actually needed.

PathSet *feels* wrong although I have not tried to refactor/reimplement
it. It provides good abstraction for basedir+includes+excludes directory
scanner, but does not work as well for individual files and collections
of files defined using other approaches. What if we introduce FileSet
interface with single getFiles method and make PathSet implement that
interface?
