import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createGroup } from '../api/groups';

function CreateGroup() {
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [errors, setErrors] = useState({});
    const [submitting, setSubmitting] = useState(false);
    const navigate = useNavigate();

    async function handleSubmit(event) {
        event.preventDefault();
        setSubmitting(true);
        setErrors({});

        try {
            const result = await createGroup(name, description || null);

            if (result.errors) {
                setErrors(result.errors);        // AC3, AC4: inline field errors
                return;
            }

            navigate(`/groups/${result.group.id}`);   // AC1: taken to the new group's page
        } catch {
            setErrors({ form: 'Could not create the group. Please try again.' });
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="page">
            <div className="card">
                <h1>Create a Group</h1>
                <p className="subtitle">Name your flat, trip or event</p>

                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label htmlFor="name">Group name</label>
                        <input
                            id="name"
                            type="text"
                            value={name}
                            placeholder="Enter a group name"
                            onChange={(event) => setName(event.target.value)}
                        />
                        {errors.name && <span className="error">{errors.name}</span>}
                    </div>

                    <div className="form-group">
                        <label htmlFor="description">Description (optional)</label>
                        <input
                            id="description"
                            type="text"
                            value={description}
                            placeholder="What is this group for?"
                            onChange={(event) => setDescription(event.target.value)}
                        />
                        {errors.description && <span className="error">{errors.description}</span>}
                    </div>

                    {errors.form && <span className="error">{errors.form}</span>}

                    <button type="submit" disabled={submitting}>
                        {submitting ? 'Creating…' : 'Create Group'}
                    </button>
                </form>
            </div>
        </div>
    );
}

export default CreateGroup;