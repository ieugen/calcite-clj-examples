(ns ieugen.calcite
  (:import [org.apache.calcite DataContext]
           [org.apache.calcite.linq4j Enumerable Linq4j QueryProvider]
           [org.apache.calcite.config
            CalciteConnectionConfig
            CalciteConnectionConfigImpl
            CalciteConnectionProperty]
           [org.apache.calcite.jdbc JavaTypeFactoryImpl CalciteSchema]
           [org.apache.calcite.plan
            ConventionTraitDef
            RelOptCluster
            RelOptPlanner
            RelOptTable$ViewExpander
            RelOptUtil]
           [org.apache.calcite.plan.volcano VolcanoPlanner]
           [org.apache.calcite.rex RexBuilder]
           [org.apache.calcite.prepare CalciteCatalogReader]
           [org.apache.calcite.schema ScannableTable]
           [org.apache.calcite.schema.impl AbstractTable]
           [org.apache.calcite.sql SqlExplainFormat SqlExplainLevel SqlNode]
           [org.apache.calcite.sql.fun SqlStdOperatorTable]
           [org.apache.calcite.sql.type SqlTypeName]
           [org.apache.calcite.sql.parser SqlParser]
           [org.apache.calcite.sql.validate
            SqlValidator
            SqlValidator$Config
            SqlValidatorUtil]
           [org.apache.calcite.rel.type
            RelDataType
            RelDataTypeFactory
            RelDataTypeFactory$Builder]
           [org.apache.calcite.sql2rel SqlToRelConverter StandardConvertletTable])
  (:gen-class))


(def book-data [[1 "Les Miserables" 1862 0]
                 [2 "The hunchback of Notre-Dame" 1829 0]])


(def author-data [[0 "Victor" "Hugo"]
                  [1 "ALexandre" "Dumas"]])


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))


(defn list-table
  [^RelDataType row-type ^java.util.List data]
  (proxy [org.apache.calcite.schema.impl.AbstractTable org.apache.calcite.schema.ScannableTable] []
    (scan [^DataContext root]
      (Linq4j/asEnumerable (data)))
    (getRowType [^RelDataTypeFactory type-factory] row-type)))

(defn make-author-type [type-factory]
  (doto (RelDataTypeFactory$Builder. type-factory)
    (.add "id" SqlTypeName/INTEGER)
    (.add "fname" SqlTypeName/VARCHAR)
    (.add "lname" SqlTypeName/VARCHAR)))

(defn make-book-type [type-factory]
  (doto (RelDataTypeFactory$Builder. type-factory)
    (.add "id" SqlTypeName/INTEGER)
    (.add "title" SqlTypeName/VARCHAR)
    (.add "year" SqlTypeName/INTEGER)
    (.add "author" SqlTypeName/INTEGER)))

(defn new-cluster [^RelDataTypeFactory factory]
  (let [planner (doto (VolcanoPlanner.)
                  (.addRelTraitDef ConventionTraitDef/INSTANCE))
        rex-builder (RexBuilder. factory)]
    (RelOptCluster/create planner rex-builder)))

(def node-expander (reify RelOptTable$ViewExpander
                     (expandView
                       [this row-type query-str schema-path view-path] nil)))

(comment

  (let [type-factory (JavaTypeFactoryImpl.)
        author-type (make-author-type type-factory)
        authors-table (list-table (.build author-type) author-data)
        book-type (make-book-type type-factory)
        books-table (list-table (.build book-type) book-data)
        schema (doto (CalciteSchema/createRootSchema true)
                 (.add "author" authors-table)
                 (.add "book" books-table))
        parser (SqlParser/create "SELECT b.id, b.title, b.\"year\", a.fname || ' ' || a.lname
                                  FROM Book b
                                  LEFT OUTER JOIN Author a ON b.author=a.id
                                  WHERE b.\"year\" > 1830
                                  ORDER BY b.id
                                  LIMIT 5")
        sql-node (.parseQuery parser)
        props (doto (java.util.Properties.)
                (.setProperty (.camelName CalciteConnectionProperty/CASE_SENSITIVE) "false"))
        config (CalciteConnectionConfigImpl. props)
        catalog-reader (CalciteCatalogReader.
                        schema (java.util.Collections/singletonList "") type-factory config)
        validator (SqlValidatorUtil/newValidator (SqlStdOperatorTable/instance)
                                                 catalog-reader
                                                 type-factory
                                                 SqlValidator$Config/DEFAULT)

        valid-node (.validate validator sql-node)
        cluster (new-cluster type-factory)
        rel-converter (SqlToRelConverter.
                       node-expander
                       validator
                       catalog-reader
                       cluster StandardConvertletTable/INSTANCE (SqlToRelConverter/config))
        log-plan (.rel (.convertQuery rel-converter valid-node false true))

        ]
    (println (RelOptUtil/dumpPlan "[Logical plan]" log-plan SqlExplainFormat/TEXT SqlExplainLevel/ALL_ATTRIBUTES)))


  0)