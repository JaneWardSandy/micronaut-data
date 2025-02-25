package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Repository
import io.micronaut.data.annotation.sql.Procedure
import io.micronaut.data.jpa.annotation.EntityGraph
import io.micronaut.data.jpa.repository.JpaSpecificationExecutor
import io.micronaut.data.jpa.repository.criteria.Specification
import io.micronaut.data.repository.CrudRepository
import io.reactivex.Maybe
import io.reactivex.Single
import jakarta.transaction.Transactional
import java.util.concurrent.CompletableFuture

// tag::join[]
// tag::async[]
// tag::specifications[]
// tag::procedure[]
@Repository
interface ProductRepository : CrudRepository<Product, Long>, JpaSpecificationExecutor<Product> {
// end::join[]
// end::async[]
// end::specifications[]
// end::procedure[]

    // tag::join[]
    @Join(value = "manufacturer", type = Join.Type.FETCH) // <1>
    fun list(): List<Product>
    // end::join[]

    // tag::entitygraph[]
    @EntityGraph(attributePaths = ["manufacturer", "title"]) // <1>
    override fun findAll(): List<Product>
    // end::entitygraph[]

    // tag::async[]
    @Join("manufacturer")
    fun findByNameContains(str: String): CompletableFuture<Product>

    fun countByManufacturerName(name: String): CompletableFuture<Long>
    // end::async[]

    // tag::reactive[]
    @Join("manufacturer")
    fun queryByNameContains(str: String): Maybe<Product>

    fun countDistinctByManufacturerName(name: String): Single<Long>
    // end::reactive[]

    // tag::procedure[]
    @Procedure(named = "calculateSum")
    fun calculateSum(productId: Long): Long

    @Procedure("calculateSumInternal")
    fun calculateSumCustom(productId: Long): Long
    // end::procedure[]

    // tag::specifications[]

    @Transactional
    fun findByName(name: String, caseInsensitive: Boolean, includeBlank: Boolean): List<Product> {
        var specification = if (caseInsensitive) {
            Specifications.nameEqualsCaseInsensitive(name)
        } else {
            Specifications.nameEquals(name)
        }
        if (includeBlank) {
            specification = specification.or(Specifications.nameEquals(""))
        }
        return findAll(specification)
    }

    // tag::spec[]
    object Specifications {

        fun nameEquals(name: String) = Specification<Product> { root, _, criteriaBuilder ->
            criteriaBuilder.equal(root.get<String>("name"), name)
        }

        fun nameEqualsCaseInsensitive(name: String) = Specification<Product> { root, _, criteriaBuilder ->
            criteriaBuilder.equal(criteriaBuilder.lower(root.get("name")), name.toLowerCase())
        }
    }
    // end::spec[]

    // end::specifications[]

// tag::join[]
// tag::async[]
// tag::specifications[]
// tag::procedure[]
}
// end::join[]
// end::async[]
// end::specifications[]
// end::procedure[]
