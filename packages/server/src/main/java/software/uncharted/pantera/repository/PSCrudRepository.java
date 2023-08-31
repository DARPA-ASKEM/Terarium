package software.uncharted.pantera.repository;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Helper Reposiroty that extends both the {@link ListPagingAndSortingRepository} and the {@link ListCrudRepository} in
 * a single interface
 */
@NoRepositoryBean
public interface PSCrudRepository<T, ID> extends ListPagingAndSortingRepository<T, ID>, ListCrudRepository<T, ID> {
}
