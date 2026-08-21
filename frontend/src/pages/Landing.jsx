import {Link} from 'react-router-dom';
import {useEffect, useState} from 'react';
import './Landing.css';
import {getCurrentUser} from "../api/users.js";

function Landing() {
    const [isLoggedIn, setIsLoggedIn] = useState(false);

    useEffect(() => {
        getCurrentUser()
            .then((user) => setIsLoggedIn(Boolean(user)))
            .catch(() =>  setIsLoggedIn(false))
    }, []);

    return (
        <div className="page landing">
            <div className="card">
                <h1>Welcome to FairShare</h1>
                <p className="subtitle">Easily split bills and manage group expenses.</p>

                <div className="actions">
                    {isLoggedIn ? (
                        <>
                            <Link to="/groups/new" className="button landingLink">Create Group</Link>
                            <Link to="/groups" className="button landingLink">My Groups</Link>
                            <Link to="/profile" className="button">My Profile</Link>
                        </>
                    ) : (
                        <>
                            <Link to="/register" className="button landingLink">Create Profile</Link>
                            <Link to="/login" className="button">Log-in</Link>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
}

export default Landing;
